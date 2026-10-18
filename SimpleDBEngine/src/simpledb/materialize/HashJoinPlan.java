package simpledb.materialize;

import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.record.Schema;

public class HashJoinPlan implements Plan {
    private Plan p1, p2;
    private String fldname1, fldname2;
    private Schema sch = new Schema();
    private Transaction tx;

    public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
        this.p1 = p1;
        this.p2 = p2;
        this.fldname1 = fldname1;
        this.fldname2 = fldname2;
        this.tx = tx;

        sch.addAll(p1.schema());
        sch.addAll(p2.schema());
    }

    public Scan open() {
        return new HashJoinScan(tx, p1, p2, fldname1, fldname2);
    }

    public int blocksAccessed() {
        return 3 * (p1.blocksAccessed() + p2.blocksAccessed());
    }

    public int recordsOutput() {
        int maxvals = Math.max(p1.distinctValues(fldname1), p2.distinctValues(fldname2));

        if (maxvals == 0) {
            return 0;
        }

        return (p1.recordsOutput() * p2.recordsOutput()) / maxvals;
    }

    public int distinctValues(String fldname) {
        if (p1.schema().hasField(fldname)) {
            return p1.distinctValues(fldname);
        }
        return p2.distinctValues(fldname);
    }

    public Schema schema() {
        return sch;
    }

    @Override 
    public String describePlan(String indent) {
        return indent + "HashJoinPlan [condition="
            + fldname1 + " = " + fldname2
            + ", blocks=" + blocksAccessed()
            + ", records=" + recordsOutput() + "]\n"
            + p1.describePlan(indent + "  ")
            + p2.describePlan(indent + "  ");
    }
}
