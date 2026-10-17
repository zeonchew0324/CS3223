package simpledb.materialize;

import java.util.*;
import simpledb.query.*;

public class DistinctScan implements Scan {
    private Scan s;
    private List<String> fields;
    private List<Constant> previous;
    private boolean first;

    public DistinctScan(Scan s, List<String> fields) {
        this.s = s;
        this.fields = fields;
        this.previous = new ArrayList<>();
        this.first = true;
    }

    public void beforeFirst() {
        s.beforeFirst();
        previous.clear();
        first = true;
    }

    public boolean next() {
        while (s.next()) {
            List<Constant> current = new ArrayList<>();

            for (String fld : fields) {
                current.add(s.getVal(fld));
            }

            if (first || !current.equals(previous)) {
                previous = current;
                first = false;
                return true;
            }
        }
        return false;
    }

    public void close() {
        s.close();
    }

    public Constant getVal(String fldname) {
        return s.getVal(fldname);
    }

    public int getInt(String fldname) {
        return s.getInt(fldname);
    }

    public String getString(String fldname) {
        return s.getString(fldname);
    }

    public boolean hasField(String fldname) {
        return s.hasField(fldname);
    }
}
