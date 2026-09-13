package simpledb.plan;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.Scan;

public class JoinPlanTest {
    public static void main(String[] args) {
        SimpleDB db = new SimpleDB("studentdb");
        Transaction tx = db.newTx();
        Planner planner = db.planner();

        // Forces join between student and dept
        String qry = "select sname, dname from student, dept where majorid = did";

        try {
            Plan p = planner.createQueryPlan(qry, tx);
            Scan s = p.open();
            System.out.println("SNAME\tDNAME");
            while (s.next()) {
                String sname = s.getString("sname");
                String dname = s.getString("dname");
                System.out.println(sname + "\t" + dname);
            }
            s.close();
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            tx.rollback();
        }
    }
}
