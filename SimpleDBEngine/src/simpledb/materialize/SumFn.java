package simpledb.materialize;

import simpledb.query.*;

public class SumFn implements AggregationFn {
    private String fldname;
    private int sum;
    private boolean empty;

    public SumFn(String fldname) {
        this.fldname = fldname;
    }

    public void processFirst(Scan s) {
        sum = s.getInt(fldname);
        empty = false;
    }

    public void processNext(Scan s) {
        sum += s.getInt(fldname);
    }

    public void processEmpty() {
        sum = 0;
        empty = true;
    }

    public String fieldName() {
        return "sumof" + fldname;
    }

    public Constant value() {
        return empty ? new Constant() : new Constant(sum);
    }

    public String sourceField() {
      return fldname;
   }
}
