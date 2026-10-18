package simpledb.materialize;

import simpledb.query.*;

public class AvgFn implements AggregationFn {
    private String fldname;
    private int sum;
    private int count;

    public AvgFn(String fldname) {
        this.fldname = fldname;
    }

    public void processFirst(Scan s) {
        sum = s.getInt(fldname);
        count = 1;
    }

    public void processNext(Scan s) {
        sum += s.getInt(fldname);
        count++;
    }

    public void processEmpty() {
        sum = 0;
        count = 0;
    }

    public String fieldName() {
        return "avgof" + fldname;
    }

    public Constant value() {
        return count == 0 ? new Constant() : new Constant(sum / count);
    }

    public String sourceField() {
      return fldname;
   }
}
