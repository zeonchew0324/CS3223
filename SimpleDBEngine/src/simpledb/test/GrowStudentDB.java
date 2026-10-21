package simpledb.test;

import java.sql.*;
import java.util.Random;
import simpledb.jdbc.embedded.EmbeddedDriver;

/**
 * Adds generated rows on top of the seed data created by CreateStudentDB,
 * so that the join experiments have enough data to time.
 * The seed rows are kept unchanged; new rows get ids that continue
 * after the seed ids. Only STUDENT and ENROLL grow; DEPT, COURSE and
 * SECTION stay as seeded, since they are small dimension tables.
 *
 *   java simpledb.test.GrowStudentDB <dir> <extraStudents> <extraEnrolls> [index]
 *
 * With "index", a B-tree index is created on ENROLL(StudentId) before
 * the new rows are inserted. IndexUpdatePlanner backfills the index
 * with the existing seed rows, so no re-insert is needed.
 * A fixed random seed makes every run produce the same data.
 */
public class GrowStudentDB {
   // seed ids: students 1-9, enroll eids 14-64, sections 13,23,33,43,53
   private static final int FIRST_NEW_SID = 10;
   private static final int FIRST_NEW_EID = 1000;
   private static final int[] SEED_SECTIONS = {13, 23, 33, 43, 53};
   private static final int[] SEED_DEPTS = {10, 20, 30};
   private static final String[] GRADES = {"A", "A-", "B+", "B", "B-", "C+", "C", "D", "F"};

   public static void main(String[] args) throws Exception {
      if (args.length < 3) {
         System.out.println("usage: GrowStudentDB <dir> <extraStudents> <extraEnrolls> [index]");
         return;
      }
      String dir = args[0];
      int extraStudents = Integer.parseInt(args[1]);
      int extraEnrolls = Integer.parseInt(args[2]);
      boolean withIndex = args.length > 3 && args[3].equalsIgnoreCase("index");
      int totalStudents = FIRST_NEW_SID - 1 + extraStudents;
      Random rand = new Random(3223);

      Driver d = new EmbeddedDriver();
      try (Connection conn = d.connect("jdbc:simpledb:" + dir, null);
           Statement stmt = conn.createStatement()) {

         long t0 = System.currentTimeMillis();
         for (int i = 0; i < extraStudents; i++) {
            int sid = FIRST_NEW_SID + i;
            stmt.executeUpdate("insert into STUDENT(SId, SName, MajorId, GradYear) values ("
                  + sid + ", 'stu" + sid + "', " + SEED_DEPTS[rand.nextInt(SEED_DEPTS.length)] + ", "
                  + (2019 + rand.nextInt(6)) + ")");
         }
         System.out.println(extraStudents + " STUDENT rows added (sid " + FIRST_NEW_SID + " to " + totalStudents + ").");

         if (withIndex) {
            stmt.executeUpdate("create index enroll_sid on ENROLL(StudentId) using btree");
            System.out.println("Index enroll_sid created on ENROLL(StudentId).");
         }

         for (int i = 0; i < extraEnrolls; i++) {
            int eid = FIRST_NEW_EID + i;
            stmt.executeUpdate("insert into ENROLL(EId, StudentId, SectionId, Grade) values (" + eid + ", "
                  + (rand.nextInt(totalStudents) + 1) + ", "
                  + SEED_SECTIONS[rand.nextInt(SEED_SECTIONS.length)] + ", '"
                  + GRADES[rand.nextInt(GRADES.length)] + "')");
            if ((i + 1) % 10000 == 0)
               System.out.println("  " + (i + 1) + " ENROLL rows...");
         }
         System.out.println(extraEnrolls + " ENROLL rows added in " + (System.currentTimeMillis() - t0) / 1000 + " s.");
      }
   }
}
