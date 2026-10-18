package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.query.*;
import simpledb.record.*;
import simpledb.plan.Plan;

public class DistinctPlan implements Plan {
    private Plan p;
    private List<String> fields;

    public DistinctPlan(Transaction tx, Plan p, List<String> fields) {
        this.p = new SortPlan(tx, p, fields);
        this.fields = fields;
    }

    public Scan open() {
        return new DistinctScan(p.open(), fields);
    }

    public int blocksAccessed() {
        return p.blocksAccessed();
    }

    public int recordsOutput() {
        return p.recordsOutput();
    }

    public int distinctValues(String fldname) {
        return p.distinctValues(fldname);
    }

    public Schema schema() {
        return p.schema();
    }

    @Override 
    public String describePlan(String indent) {
        return indent + "DistinctPlan [fields=" + fields + "]\n"
            + p.describePlan(indent + "  ");
    }
}
