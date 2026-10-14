package simpledb.materialize;

import simpledb.query.Constant;
import simpledb.query.Scan;

public class EmptyScan implements Scan {
    public void beforeFirst() {}

    public boolean next() {
        return false;
    }

    public void close () {}

    public Constant getVal(String fldname) {
        throw new IllegalStateException("Empty scan has no current record");
    }

    public int getInt(String fldname) {
        throw new IllegalStateException("Empty scan has no current record");
    }

    public String getString(String fldname) {
        throw new IllegalStateException("Empty scan has no current record");
    }

    public boolean hasField(String fldname) {
        return false;
    }
}
