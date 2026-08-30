package simpledb.plan;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.Scan;

public class PlanTest1 {
   public static void main(String[] args) {
      SimpleDB db = new SimpleDB("studentdb");
      Transaction tx = db.newTx();

      String qry = "select sid, sname, gradyear from student order by gradyear asc, sname desc";

      Plan p = db.planner().createQueryPlan(qry, tx);
      Scan s = p.open();
      while (s.next())
         System.out.println(s.getInt("sid") + "\t" + s.getString("sname")
                          + "\t" + s.getInt("gradyear"));
      s.close();
      tx.commit();
   }
}