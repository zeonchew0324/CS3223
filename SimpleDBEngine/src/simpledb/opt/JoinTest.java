package simpledb.opt;

import java.util.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;

/**
 * Test program for the join algorithms supported by the
 * HeuristicQueryPlanner / TablePlanner.
 * <p>
 * Each query is run four times against the student database:
 * once letting the planner choose the join algorithm ("auto"), and
 * once with each algorithm forced (index join, sort-merge join,
 * nested-loops join) via the system property
 * {@link TablePlanner#JOIN_PROPERTY}.  The planner prints the access
 * path it picked for every table, the program prints the result rows,
 * and finally checks that all four runs returned the same rows.
 * <p>
 * Usage: <code>java simpledb.opt.JoinTest [dbname]</code>
 * (default database name is "studentdb").
 */
public class JoinTest {
   private static final String[] MODES = {"auto", "index", "merge", "nested"};

   private static final String[] QUERIES = {
      // equijoin, no index on either join field (dept.did)
      "select sname, dname from student, dept where majorid = did",
      // equijoin, hash index on enroll.studentid
      "select sname, grade from student, enroll where sid = studentid",
      // equijoin with a selection on the table with the index
      "select sname, grade from student, enroll where sid = studentid and grade = 'A'",
      // equijoin with a selection on the other table, btree index on student.majorid
      "select sname, dname from student, dept where majorid = did and gradyear = 2020",
      // three-way join (index on enroll.studentid, none on dept.did)
      "select sname, dname, grade from student, dept, enroll where majorid = did and sid = studentid",
      // four-way join chain
      "select sname, grade, prof, title from student, enroll, section, course "
         + "where sid = studentid and sectionid = sectid and courseid = cid",
      // non-equijoin: only the nested-loops join can evaluate it
      "select sname, gradyear, prof, yearoffered from student, section where gradyear < yearoffered",
      // equijoin plus an extra non-equality join term
      "select sname, prof from student, enroll, section "
         + "where sid = studentid and sectionid = sectid and gradyear > yearoffered",
      // join whose result is empty after selection
      "select sname, dname from student, dept where majorid = did and gradyear = 1999",
      // join combined with order by
      "select sname, dname from student, dept where majorid = did order by dname, sname desc",
   };

   public static void main(String[] args) {
      String dbname = (args.length > 0) ? args[0] : "studentdb";
      SimpleDB db = new SimpleDB(dbname);
      Planner planner = db.planner();

      int failures = 0;
      for (String qry : QUERIES) {
         System.out.println();
         System.out.println("==================================================================");
         System.out.println("QUERY: " + qry);
         Map<String,List<String>> results = new LinkedHashMap<>();
         for (String mode : MODES) {
            System.setProperty(TablePlanner.JOIN_PROPERTY, mode);
            System.out.println("--- join mode: " + mode);
            List<String> rows = run(db, planner, qry);
            results.put(mode, rows);
         }
         System.setProperty(TablePlanner.JOIN_PROPERTY, "auto");

         // every mode must return the same multiset of rows
         List<String> expected = sorted(results.get("auto"));
         boolean ok = true;
         for (String mode : MODES)
            if (!sorted(results.get(mode)).equals(expected))
               ok = false;
         System.out.println("RESULT: " + expected.size() + " rows; all join modes agree: " + ok);
         if (!ok)
            failures++;
      }
      System.out.println();
      System.out.println(failures == 0 ? "ALL QUERIES CONSISTENT ACROSS JOIN ALGORITHMS"
                                       : failures + " QUERIES GAVE INCONSISTENT RESULTS");
   }

   private static List<String> run(SimpleDB db, Planner planner, String qry) {
      Transaction tx = db.newTx();
      Plan p = planner.createQueryPlan(qry, tx);
      List<String> fields = p.schema().fields();
      List<String> rows = new ArrayList<>();
      Scan s = p.open();
      while (s.next()) {
         StringBuilder sb = new StringBuilder();
         for (String fld : fields) {
            if (sb.length() > 0)
               sb.append(", ");
            sb.append(fld).append("=").append(s.getVal(fld));
         }
         rows.add(sb.toString());
      }
      s.close();
      tx.commit();
      for (String row : rows)
         System.out.println("    " + row);
      System.out.println("    (" + rows.size() + " rows)");
      return rows;
   }

   private static List<String> sorted(List<String> rows) {
      List<String> copy = new ArrayList<>(rows);
      Collections.sort(copy);
      return copy;
   }
}
