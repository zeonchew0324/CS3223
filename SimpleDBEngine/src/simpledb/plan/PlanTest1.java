package simpledb.plan;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.*;
import simpledb.parse.*;

public class PlanTest1 {
    public static void main(String[] args) {
        // start the db
        SimpleDB db = new SimpleDB("studentdb");

        // create a transaction
        Transaction tx = db.newTx();

        try {
            // create query
            String query = "select sid, sname, gradyear from student order by gradyear asc, sname desc";

            // parse query
            Parser parser = new Parser(query);
            QueryData data = parser.query();

            // create query plan
            QueryPlanner planner = new BasicQueryPlanner(db.mdMgr());
            Plan p = planner.createPlan(data, tx);

            Scan s = p.open();

            // print col names
            for (String fldname : p.schema().fields()) {
                System.out.print(fldname + "\t");
            }
            System.out.println();

            // print records
            while (s.next()) {
                System.out.print(s.getInt("sid") + "\t");
                System.out.print(s.getString("sname") + "\t");
                System.out.print(s.getInt("gradyear") + "\t");

                System.out.println();
            }

            s.close();
            tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
            tx.rollback();
        }
    }
}