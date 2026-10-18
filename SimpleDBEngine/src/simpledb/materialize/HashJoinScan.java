package simpledb.materialize;

import java.util.*;

import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

public class HashJoinScan implements Scan {
    private static final int NUM_PARTITIONS = 10;

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

    private TempTable[] partitionInput(Plan p, String fldname, Transaction tx) {
        TempTable[] parts = new TempTable[NUM_PARTITIONS];
        Schema schema = p.schema();

        for (int i = 0; i < NUM_PARTITIONS; i++) {
            parts[i] = new TempTable(tx, schema);
        }

        Scan src = p.open();

        try {
            while (src.next()) {
                Constant key = src.getVal(fldname);
                int index = Math.floorMod(key.hashCode(), NUM_PARTITIONS);

                UpdateScan dest = parts[index].open();

                try {
                    dest.insert();
                    for (String fld : schema.fields()) {
                        dest.setVal(fld, src.getVal(fld));
                    }
                }
                finally {
                    dest.close();
                }
            }
        }
        finally {
            src.close();
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

            if (partition + 1 >= NUM_PARTITIONS) {
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
