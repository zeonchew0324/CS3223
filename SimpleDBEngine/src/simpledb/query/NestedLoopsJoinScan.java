package simpledb.query;

/**
 * The scan class corresponding to the <i>nested loops join</i> relational
 * algebra operator.
 */
public class NestedLoopsJoinScan implements Scan {
    private Scan s1, s2;
    private Predicate joinpred;

    /**
     * Create a nested loops join scan having the two underlying scans
     * and a join predicate.
     * @param s1 the LHS scan
     * @param s2 the RHS scan
     * @param joinpred the join predicate
     */
    public NestedLoopsJoinScan(Scan s1, Scan s2, Predicate joinpred) {
        this.s1 = s1;
        this.s2 = s2;
        this.joinpred = joinpred;
        beforeFirst();
    }

    /**
     * Position the scan before its first record.
     * In particular, the LHS scan is positioned at
     * its first record, and the RHS scan
     * is positioned before its first record.
     * @see simpledb.query.Scan#beforeFirst()
     */
    @Override
    public void beforeFirst() {
        s1.beforeFirst();
        s1.next();
        s2.beforeFirst();
    }

    /**
     * Move the scan to the next record.
     * The method continually moves to the next RHS record until it finds
     * a pair that satisfies the join predicate.
     * If there are no more RHS records, it moves to the next LHS record and the
     * first RHS record, repeating the evaluation.
     * If there are no more LHS records, the method returns false.
     * @see simpledb.query.Scan#next()
     */
    @Override
    public boolean next() {
        while (true) {
            if (s2.next()) {
                if (joinpred.isSatisfied(this))
                    return true;
            } else {
                s2.beforeFirst();
                if (!s1.next())
                    return false;
            }
        }
    }

    /**
     * Return the integer value of the specified field.
     * The value is obtained from whichever scan
     * contains the field.
     * @see simpledb.query.Scan#getInt(java.lang.String)
     */
    @Override
    public int getInt(String fldname) {
        if (s1.hasField(fldname))
            return s1.getInt(fldname);
        else
            return s2.getInt(fldname);
    }

    /**
     * Returns the string value of the specified field.
     * The value is obtained from whichever scan
     * contains the field.
     * @see simpledb.query.Scan#getString(java.lang.String)
     */
    @Override
    public String getString(String fldname) {
        if (s1.hasField(fldname))
            return s1.getString(fldname);
        else
            return s2.getString(fldname);
    }

    /**
     * Return the value of the specified field.
     * The value is obtained from whichever scan
     * contains the field.
     * @see simpledb.query.Scan#getVal(java.lang.String)
     */
    @Override
    public Constant getVal(String fldname) {
        if (s1.hasField(fldname))
            return s1.getVal(fldname);
        else
            return s2.getVal(fldname);
    }

    /**
     * Returns true if the specified field is in
     * either of the underlying scans.
     * @see simpledb.query.Scan#hasField(java.lang.String)
     */
    @Override
    public boolean hasField(String fldname) {
        return s1.hasField(fldname) || s2.hasField(fldname);
    }

    /**
     * Close both underlying scans.
     * @see simpledb.query.Scan#close()
     */
    @Override
    public void close() {
        s1.close();
        s2.close();
    }
}
