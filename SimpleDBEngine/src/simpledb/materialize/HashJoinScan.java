package simpledb.materialize;

import java.util.*;

import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

public class HashJoinScan implements Scan {
    /** Upper bound on partitions; the actual count is limited by free buffers. */
    private static final int MAX_PARTITIONS = 10;

    /**
     * Number of partitions used for both inputs. Each partition keeps one
     * scan open while an input is being partitioned, and the input's own
     * scan needs buffers too (it may itself be a join), so the count is
     * taken from the buffers still free after the first input is opened,
     * capped at MAX_PARTITIONS. Both inputs must use the same count.
     */
    private int numPartitions = 0;

    private Plan p1, p2;
    private String fldname1, fldname2;
    private Schema sch1, sch2;

    private TempTable[] parts1, parts2;
    private Scan s1;
    private int partition = -1;

    private Map<String, Constant> leftRow;
    private Map<String, Constant> rightRow;

    private Map<Constant, List<Map<String, Constant>>> hashTable;
    private Iterator<Map<String, Constant>> matches;

    public HashJoinScan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
        this.p1 = p1;
        this.p2 = p2;
        this.fldname1 = fldname1;
        this.fldname2 = fldname2;
        this.sch1 = p1.schema();
        this.sch2 = p2.schema();

        this.parts1 = partitionInput(p1, fldname1, tx);
        this.parts2 = partitionInput(p2, fldname2, tx);
    }

    /**
     * Write every record of p into one of numPartitions temporary tables,
     * chosen by hashing the join field. One scan per partition stays open
     * for the whole pass, so each insert continues from the partition's
     * current block instead of re-reading it from block 0.
     */
    private TempTable[] partitionInput(Plan p, String fldname, Transaction tx) {
        Schema schema = p.schema();

        // Open the input first: if it is itself a join, opening it may pin
        // buffers (or partition its own inputs). Only then decide how many
        // partition scans the remaining buffers can hold.
        Scan src = p.open();
        if (numPartitions == 0) {
            numPartitions = Math.max(2, Math.min(MAX_PARTITIONS, tx.availableBuffs() - 2));
        }

        TempTable[] parts = new TempTable[numPartitions];
        UpdateScan[] dests = new UpdateScan[numPartitions];

        try {
            for (int i = 0; i < numPartitions; i++) {
                parts[i] = new TempTable(tx, schema);
                dests[i] = parts[i].open();
            }

            while (src.next()) {
                Constant key = src.getVal(fldname);
                UpdateScan dest = dests[Math.floorMod(key.hashCode(), numPartitions)];

                dest.insert();
                for (String fld : schema.fields()) {
                    dest.setVal(fld, src.getVal(fld));
                }
            }
        }
        finally {
            src.close();
            for (UpdateScan dest : dests) {
                if (dest != null) {
                    dest.close();
                }
            }
        }

        return parts;
    }

    private Map<String, Constant> readRow(Scan s, Schema schema) {
        Map<String, Constant> row = new HashMap<>();
        for (String fld : schema.fields()) {
            row.put(fld, s.getVal(fld));
        }
        return row;
    }

    private void startPartition(int index) {
        if (s1 != null) {
            s1.close();
        }

        partition = index;
        hashTable = new HashMap<>();
        matches = null;

        Scan s2 = parts2[partition].open();

        try {
            while (s2.next()) {
                Map<String, Constant> row = readRow(s2, sch2);
                Constant key = row.get(fldname2);

                hashTable.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }
        finally {
            s2.close();
        }
        s1 = parts1[partition].open();
    }

    public boolean next() {
        while (true) {
            if (matches != null && matches.hasNext()) {
                rightRow = matches.next();
                return true;
            }

            if (s1 != null && s1.next()) {
                leftRow = readRow(s1, sch1);
                Constant key = leftRow.get(fldname1);
                List<Map<String, Constant>> found = hashTable.get(key);
                matches = found == null ? Collections.emptyIterator() : found.iterator();
                continue;
            }

            if (partition + 1 >= numPartitions) {
                return false;
            }

            startPartition(partition + 1);
        }
    }

    public void beforeFirst() {
        if (s1 != null) {
            s1.close();
            s1 = null;
        }

        partition = -1;
        leftRow = null;
        rightRow = null;
        matches = null;
        hashTable = null;
    }

    public Constant getVal(String fldname) {
        if (sch1.hasField(fldname)) {
            return leftRow.get(fldname);
        }
        return rightRow.get(fldname);
    }

    public int getInt(String fldname) {
        return getVal(fldname).asInt();
    }

    public String getString(String fldname) {
        return getVal(fldname).asString();
    }

    public boolean hasField(String fldname) {
        return sch1.hasField(fldname) || sch2.hasField(fldname);
    }

    public void close() {
        if (s1 != null) {
            s1.close();
            s1 = null;
        }
    }
}
