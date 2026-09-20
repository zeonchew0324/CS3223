package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.plan.Plan;
import simpledb.query.*;

public class HashJoinPlan implements Plan {
    private Transaction tx;
    private Plan build, probe;
    private String buildField, probeField;
    private Schema sch = new Schema();

    public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
        this.tx = tx;
        if (p1.recordsOutput() < p2.recordsOutput()) {
            build = p1;
            buildField = fldname1;
            probe = p2;
            probeField = fldname2;
        } else {
            build = p2;
            buildField = fldname2;
            probe = p1;
            probeField = fldname1;
        }

        sch.addAll(build.schema());
        sch.addAll(probe.schema());
    }

    public Scan open() {
        int numBuffers = Math.max(1 , tx.availableBuffs() - 2);
        List<TempTable> buildpartitions = partition(build, buildField, numBuffers);
        List<TempTable> probepartitions = partition(probe, probeField, numBuffers);
        return new HashJoinScan(buildpartitions, probepartitions, build.schema(), probe.schema(), buildField, probeField);
    }

    private List<TempTable> partition(Plan p, String fldname, int numBuffers) {
        Schema sch = p.schema();
        List<TempTable> parts = new ArrayList<>();
        List<UpdateScan> outs = new ArrayList<>();
        for (int i = 0; i < numBuffers; i++) {
            TempTable part = new TempTable(tx, sch);
            parts.add(part);
            outs.add(part.open());
        }
        Scan src = p.open();
        while (src.next()) {
            int bucket = Math.floorMod(src.getVal(fldname).hashCode(), numBuffers);
            UpdateScan out = outs.get(bucket);
            out.insert();
            for (String f: sch.fields()) {
                out.setVal(f, src.getVal(f));
            }
        }    
        src.close();
        for (UpdateScan s: outs)
            s.close();
        return parts;    
    }

    public int blocksAccessed() {
        int b1 = new MaterializePlan(tx, build).blocksAccessed();
        int b2 = new MaterializePlan(tx, probe).blocksAccessed();
        return 3 * (b1 + b2);
    }

    public int recordsOutput() {
        int maxvals = Math.max(build.distinctValues(buildField), probe.distinctValues(probeField));
        return (build.recordsOutput() * probe.recordsOutput()) / maxvals;
    }

    public int distinctValues(String fldname) {
        if (build.schema().hasField(fldname))
            return build.distinctValues(fldname);
        else
            return probe.distinctValues(fldname);
    }

    public Schema schema() {
        return sch;
    }
}