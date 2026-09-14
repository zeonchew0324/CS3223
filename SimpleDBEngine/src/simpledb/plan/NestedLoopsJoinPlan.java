package simpledb.plan;

import simpledb.query.NestedLoopsJoinScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * The Plan class corresponding to the <i>nested loops join</i>
 * relational algebra operator.
 */
public class NestedLoopsJoinPlan implements Plan {
    private Plan p1, p2;
    private Predicate joinpred;
    private Schema schema = new Schema();

    /**
     * Creates a new nested loops join node in the query tree,
     * having the two specified subqueries and join predicate.
     * @param p1 the left-hand subquery
     * @param p2 the right-hand subquery
     * @param joinpred the join predicate
     */
    public NestedLoopsJoinPlan(Plan p1, Plan p2, Predicate joinpred) {
        this.p1 = p1;
        this.p2 = p2;
        this.joinpred = joinpred;
        schema.addAll(p1.schema());
        schema.addAll(p2.schema());
    }

    /**
     * Creates a nested loops join scan for this query.
     * @see simpledb.plan.Plan#open()
     */
    @Override
    public Scan open() {
        Scan s1 = p1.open();
        Scan s2 = p2.open();
        return new NestedLoopsJoinScan(s1, s2, joinpred);
    }

    /**
     * Estimates the number of block accesses in the join.
     * The formula is:
     * <pre> B(join(p1,p2)) = B(p1) + R(p1)*B(p2) </pre>
     * @see simpledb.plan.Plan#blocksAccessed()
     */
    @Override
    public int blocksAccessed() {
        return p1.blocksAccessed() + p1.recordsOutput() * p2.blocksAccessed();
    }

    /**
     * Estimates the number of output records in the join.
     * The formula is a simplified heuristic:
     * <pre> R(join(p1,p2)) = (R(p1)*R(p2)) / 2 </pre>
     * @see simpledb.plan.Plan#recordsOutput()
     */
    @Override
    public int recordsOutput() {
        // Simplified heuristic for join output size
        return (p1.recordsOutput() * p2.recordsOutput()) / 2;
    }

    /**
     * Estimates the distinct number of field values in the join.
     * Since the join does not inherently increase or decrease field values,
     * the estimate is the same as in the appropriate underlying query.
     * @see simpledb.plan.Plan#distinctValues(java.lang.String)
     */
    @Override
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
    @Override
    public Schema schema() {
        return schema;
    }
}
