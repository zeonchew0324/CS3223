package simpledb.plan;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.Scan;

/**
 * Tests aggregate functions (sum, count, avg, min, max)
 * with and without a group by clause, through the planner.
 */
public class AggregateTest {
   public static void main(String[] args) {
      SimpleDB db = new SimpleDB("studentdb");
      Transaction tx = db.newTx();
      Planner planner = db.planner();

      String[] queries = {
         // aggregates with no group by: one row over the whole table
         "select count(sid), sum(gradyear), avg(gradyear), min(gradyear), max(gradyear) from student",

         // single aggregate, no group by
         "select max(gradyear) from student",

         // group by with aggregates
         "select majorid, count(sid), max(gradyear) from student group by majorid",

         // group by with an order by on the aggregate output name
         "select majorid, avg(gradyear) from student group by majorid order by avgofgradyear desc",

         // group by with a where clause applied before grouping
         "select majorid, count(sid) from student where gradyear = 2020 group by majorid",

         // group by over a join
         "select dname, count(sid) from student, dept where majorid = did group by dname",

         // min/max on a string field
         "select majorid, min(sname), max(sname) from student group by majorid"
      };

      try {
         for (String qry : queries) {
            System.out.println("\n== " + qry);
            Plan p = planner.createQueryPlan(qry, tx);
            Scan s = p.open();

            for (String fldname : p.schema().fields())
               System.out.print(fldname + "\t");
            System.out.println();

            while (s.next()) {
               for (String fldname : p.schema().fields())
                  System.out.print(s.getVal(fldname) + "\t");
               System.out.println();
            }
            s.close();
         }
         tx.commit();
      } catch (Exception e) {
         e.printStackTrace();
         tx.rollback();
      }
   }
}