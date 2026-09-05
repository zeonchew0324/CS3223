package simpledb.opt;

import java.util.Map;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.query.*;
import simpledb.metadata.*;
import simpledb.index.planner.*;
import simpledb.materialize.MergeJoinPlan;
import simpledb.multibuffer.MultibufferProductPlan;
import simpledb.plan.*;

/**
 * This class contains methods for planning a single table.
 * <p>
 * When the table is joined to the plan built so far, the planner
 * considers three join algorithms:
 * <ul>
 * <li>an <i>index join</i>, when the table has an index on a field
 *     that an equality term equates with a field of the current plan;</li>
 * <li>a <i>sort-merge join</i>, when there is an equality term
 *     between a field of the table and a field of the current plan;</li>
 * <li>a <i>nested-loops join</i>, which handles any join predicate
 *     (including non-equality terms) and is therefore always applicable.</li>
 * </ul>
 * The candidate with the lowest estimated number of block accesses
 * is chosen.  For experimentation the choice can be forced with the
 * system property {@value #JOIN_PROPERTY} set to
 * <code>index</code>, <code>merge</code> or <code>nested</code>
 * (any other value, or no value, means the cost-based choice).
 * @author Edward Sciore
 */
class TablePlanner {
   /**
    * Name of the system property that forces a join algorithm.
    * Valid values are "index", "merge", "nested" and "auto" (default).
    */
   public static final String JOIN_PROPERTY = "simpledb.join";

   private TablePlan myplan;
   private Predicate mypred;
   private Schema myschema;
   private Map<String,IndexInfo> indexes;
   private Transaction tx;
   private String tblname;
   private String description = "";

   /**
    * Creates a new table planner.
    * The specified predicate applies to the entire query.
    * The table planner is responsible for determining
    * which portion of the predicate is useful to the table,
    * and when indexes are useful.
    * @param tblname the name of the table
    * @param mypred the query predicate
    * @param tx the calling transaction
    */
   public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm) {
      this.tblname = tblname;
      this.mypred  = mypred;
      this.tx  = tx;
      myplan   = new TablePlan(tx, tblname, mdm);
      myschema = myplan.schema();
      indexes  = mdm.getIndexInfo(tblname, tx);
   }

   /**
    * Returns the name of the table being planned.
    * @return the table name
    */
   public String tableName() {
      return tblname;
   }

   /**
    * Returns a one-line description of the plan most recently
    * constructed by this planner (access path, join algorithm and
    * the estimated costs of the join candidates).  The query planner
    * prints it for the plans it actually chooses.
    * @return the description of the most recently constructed plan
    */
   public String description() {
      return description;
   }

   /**
    * Constructs a select plan for the table.
    * The plan will use an indexselect, if possible.
    * @return a select plan for the table.
    */
   public Plan makeSelectPlan() {
      Plan p = makeIndexSelect();
      if (p == null) {
         p = myplan;
         description = tblname + ": tablescan";
      }
      Predicate selectpred = mypred.selectSubPred(myschema);
      if (selectpred != null)
         description += " select[" + selectpred + "]";
      return addSelectPred(p);
   }

   /**
    * Constructs a join plan of the specified plan
    * and the table.  Every applicable join algorithm
    * (index join, sort-merge join, nested-loops join) is
    * considered and the one with the lowest estimated
    * number of block accesses is returned, unless a
    * particular algorithm is forced via the system property
    * {@value #JOIN_PROPERTY}.
    * The method returns null if no join is possible,
    * i.e. if the predicate has no term relating the table
    * to the current plan.
    * @param current the specified plan
    * @return a join plan of the plan and this table
    */
   public Plan makeJoinPlan(Plan current) {
      Schema currsch = current.schema();
      Predicate joinpred = mypred.joinSubPred(myschema, currsch);
      if (joinpred == null) {
         description = "";
         return null;
      }

      Plan indexjoin  = makeIndexJoin(current, currsch, joinpred);
      Plan mergejoin  = makeMergeJoin(current, currsch, joinpred);
      Plan nestedjoin = makeNestedLoopJoin(current, joinpred);

      String costs = "indexjoin=" + cost(indexjoin)
                   + " mergejoin=" + cost(mergejoin)
                   + " nestedloopjoin=" + cost(nestedjoin);

      String forced = System.getProperty(JOIN_PROPERTY, "auto").trim().toLowerCase();
      Plan chosen;
      String how;
      if (forced.equals("index") && indexjoin != null) {
         chosen = indexjoin;
         how = "indexjoin (forced)";
      }
      else if (forced.equals("merge") && mergejoin != null) {
         chosen = mergejoin;
         how = "mergejoin (forced)";
      }
      else if (forced.equals("nested")) {
         chosen = nestedjoin;
         how = "nestedloopjoin (forced)";
      }
      else {
         // cost-based choice; ties go to the earlier candidate
         chosen = indexjoin;
         how = "indexjoin";
         if (chosen == null || (mergejoin != null && mergejoin.blocksAccessed() < chosen.blocksAccessed())) {
            chosen = mergejoin;
            how = "mergejoin";
         }
         if (chosen == null || nestedjoin.blocksAccessed() < chosen.blocksAccessed()) {
            chosen = nestedjoin;
            how = "nestedloopjoin";
         }
         if (!forced.equals("auto"))
            how += " (forced '" + forced + "' not applicable)";
      }
      description = tblname + ": " + how + "[" + joinpred + "] {" + costs + "}";
      return chosen;
   }

   /**
    * Constructs a product plan of the specified plan and
    * this table.
    * @param current the specified plan
    * @return a product plan of the specified plan and this table
    */
   public Plan makeProductPlan(Plan current) {
      Plan p = addSelectPred(myplan);
      description = tblname + ": product (multibuffer)";
      return new MultibufferProductPlan(tx, current, p);
   }

   private Plan makeIndexSelect() {
      for (String fldname : indexes.keySet()) {
         Constant val = mypred.equatesWithConstant(fldname);
         if (val != null) {
            IndexInfo ii = indexes.get(fldname);
            description = tblname + ": indexselect on " + fldname;
            return new IndexSelectPlan(myplan, ii, val);
         }
      }
      return null;
   }

   /**
    * Constructs an index join, if the table has an index on a field
    * that the join predicate equates with a field of the current plan.
    * Only equality terms are considered, which is all that a hash
    * index can support anyway.
    * The index join enforces that equality itself; the table's own
    * selection terms and any remaining join terms are applied by a
    * select on top of it.
    * @return the index join plan, or null if no index is usable
    */
   private Plan makeIndexJoin(Plan current, Schema currsch, Predicate joinpred) {
      for (String fldname : indexes.keySet()) {
         String outerfield = joinpred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            IndexInfo ii = indexes.get(fldname);
            Plan p = new IndexJoinPlan(current, myplan, ii, outerfield);
            p = addSelectPred(p);
            return addJoinPred(p, joinpred.withoutEquality(fldname, outerfield));
         }
      }
      return null;
   }

   /**
    * Constructs a sort-merge join, if the join predicate contains an
    * equality term between a field of this table and a field of the
    * current plan.  The current plan is the LHS and the (selected)
    * table is the RHS; both are sorted on their join field by
    * MergeJoinPlan.  Any remaining join terms are applied by a select.
    * @return the merge join plan, or null if there is no equality term
    */
   private Plan makeMergeJoin(Plan current, Schema currsch, Predicate joinpred) {
      for (String fldname : myschema.fields()) {
         String outerfield = joinpred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan p = new MergeJoinPlan(tx, current, makeSelectPlan(), outerfield, fldname);
            return addJoinPred(p, joinpred.withoutEquality(fldname, outerfield));
         }
      }
      return null;
   }

   /**
    * Constructs a nested-loops join of the current plan (outer)
    * and the selected table (inner).  The whole join predicate is
    * evaluated inside the join scan, so no select is added on top.
    * This join works for any predicate and is always applicable.
    * @return the nested-loops join plan
    */
   private Plan makeNestedLoopJoin(Plan current, Predicate joinpred) {
      return new NestedLoopJoinPlan(current, makeSelectPlan(), joinpred);
   }

   private Plan addSelectPred(Plan p) {
      Predicate selectpred = mypred.selectSubPred(myschema);
      if (selectpred != null)
         return new SelectPlan(p, selectpred);
      else
         return p;
   }

   private Plan addJoinPred(Plan p, Predicate residual) {
      if (residual != null)
         return new SelectPlan(p, residual);
      else
         return p;
   }

   private static String cost(Plan p) {
      return (p == null) ? "n/a" : "B" + p.blocksAccessed();
   }
}
