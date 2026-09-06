package simpledb.plan;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.Scan;

public class PlanTest1 {
    public static void main(String[] args) {
        SimpleDB db = new SimpleDB("studentdb");
        Transaction tx = db.newTx();
        Planner planner = db.planner();

        String qry = "select sid, sname, gradyear from student order by gradyear asc, sname desc";

        try {
            Plan p = planner.createQueryPlan(qry, tx);
            Scan s = p.open();
            System.out.println("SID\tSNAME\tGRADYEAR");
            while (s.next()) {
                int sid = s.getInt("sid");
                String sname = s.getString("sname");
                int gradyear = s.getInt("gradyear");
                System.out.println(sid + "\t" + sname + "\t" + gradyear);
            }
            s.close();
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            tx.rollback();
        }
    }
}