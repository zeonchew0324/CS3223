package simpledb.plan;

import simpledb.query.NestedLoopJoinScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

/** The Plan class corresponding to the <i>nested-loops join</i>
  * relational algebra operator.
  * It is adapted from ProductPlan, but carries the join predicate
  * so that the scan it opens returns only the matching records.
  * @author Edward Sciore
  */
public class NestedLoopJoinPlan implements Plan {
   private Plan p1, p2;
   private Predicate pred;
   private Schema schema = new Schema();

   /**
    * Creates a new nested-loops join node in the query tree,
    * having the two specified subqueries and join predicate.
    * @param p1 the left-hand (outer) subquery
    * @param p2 the right-hand (inner) subquery
    * @param pred the join predicate
    */
   public NestedLoopJoinPlan(Plan p1, Plan p2, Predicate pred) {
      this.p1 = p1;
      this.p2 = p2;
      this.pred = pred;
      schema.addAll(p1.schema());
      schema.addAll(p2.schema());
   }

   /**
    * Creates a nested-loops join scan for this query.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      return new NestedLoopJoinScan(s1, s2, pred);
   }

   /**
    * Estimates the number of block accesses in the join.
    * The inner subquery is rescanned once per outer record, so
    * the formula is the same as for the product:
    * <pre> B(nljoin(p1,p2)) = B(p1) + R(p1)*B(p2) </pre>
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      return p1.blocksAccessed() + (p1.recordsOutput() * p2.blocksAccessed());
   }

   /**
    * Estimates the number of output records in the join,
    * which is the size of the product reduced by the
    * reduction factor of the join predicate:
    * <pre> R(nljoin(p1,p2)) = R(p1)*R(p2) / RF(pred) </pre>
    * For an equijoin F1=F2 this is R(p1)*R(p2)/max{V(p1,F1),V(p2,F2)},
    * the same estimate that MergeJoinPlan uses.
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      return (p1.recordsOutput() * p2.recordsOutput()) / pred.reductionFactor(this);
   }

   /**
    * Estimates the distinct number of field values in the join.
    * Since the join does not increase or decrease field values,
    * the estimate is the same as in the appropriate underlying query.
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   /**
    * Returns the schema of the join,
    * which is the union of the schemas of the underlying queries.
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return schema;
   }

   /**
    * Returns the join predicate evaluated by this plan.
    * @return the join predicate
    */
   public Predicate predicate() {
      return pred;
   }
}
