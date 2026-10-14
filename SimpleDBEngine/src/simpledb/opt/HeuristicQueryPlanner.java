package simpledb.opt;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.QueryData;
import simpledb.plan.*;
<<<<<<< HEAD
import simpledb.materialize.SortPlan;
=======
import simpledb.materialize.*;
>>>>>>> 75f870d (Implement Lab 5 aggregation)

/**
 * A query planner that optimizes using a heuristic-based algorithm.
 * @author Edward Sciore
 */
public class HeuristicQueryPlanner implements QueryPlanner {
   private Collection<TablePlanner> tableplanners = new ArrayList<>();
   private MetadataMgr mdm;
   
   public HeuristicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Creates an optimized left-deep query plan using the following
    * heuristics.
    * H1. Choose the smallest table (considering selection predicates)
    * to be first in the join order.
    * H2. Add the table to the join order which
    * results in the smallest output.
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      
      // Step 1:  Create a TablePlanner object for each mentioned table
      for (String tblname : data.tables()) {
         TablePlanner tp = new TablePlanner(tblname, data.pred(), tx, mdm);
         tableplanners.add(tp);
      }
      
      // Step 2:  Choose the lowest-size plan to begin the join order
      Plan currentplan = getLowestSelectPlan();
      
      // Step 3:  Repeatedly add a plan to the join order
      while (!tableplanners.isEmpty()) {
         Plan p = getLowestJoinPlan(currentplan);
         if (p != null)
            currentplan = p;
         else  // no applicable join
            currentplan = getLowestProductPlan(currentplan);
      }
      
      // Step 4.  Project on the field names and return
      // return new ProjectPlan(currentplan, data.fields());
      List<AggregationFn> aggfns = new ArrayList<>();

      for (simpledb.parse.AggregateData agg : data.aggregates()) {
         String function = agg.function().toLowerCase();
         String field = agg.field();

         switch (function) {
            case "count":
               aggfns.add(new CountFn(field));
               break;
            case "sum":
               aggfns.add(new SumFn(field));
               break;
            case "avg":
               aggfns.add(new AvgFn(field));
               break;
            case "min":
               aggfns.add(new MinFn(field));
               break;
            case "max":
               aggfns.add(new MaxFn(field));
               break;
            default:
               throw new IllegalArgumentException(
                  "Unknown aggregate function: " + function
               );
         }
      }

      if (!aggfns.isEmpty() || !data.groupFields().isEmpty()) {
         currentplan = new GroupByPlan(tx, currentplan, data.groupFields(), aggfns);
      }

      List<String> outputFields = new ArrayList<>(data.fields());

      for (AggregationFn fn : aggfns) {
         outputFields.add(fn.fieldName());
      }

      Plan result = new ProjectPlan(currentplan, outputFields);

      if (!data.sortFields().isEmpty()) {
         result = new SortPlan(tx, result, data.sortFields());
      }

      // return new ProjectPlan(currentplan, outputFields);
      return result;
   }
   
   private Plan getLowestSelectPlan() {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeSelectPlan();
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }
   
   private Plan getLowestJoinPlan(Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeJoinPlan(current);
         if (plan != null && (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput())) {
            besttp = tp;
            bestplan = plan;
         }
      }
      if (bestplan != null)
         tableplanners.remove(besttp);
      return bestplan;
   }
   
   private Plan getLowestProductPlan(Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeProductPlan(current);
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }

   public void setPlanner(Planner p) {
      // for use in planning views, which
      // for simplicity this code doesn't do.
   }
}
