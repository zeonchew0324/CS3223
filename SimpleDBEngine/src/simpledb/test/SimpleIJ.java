package simpledb.test;

import java.util.Scanner;

import static java.sql.Types.INTEGER;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class SimpleIJ {
   public static void main(String[] args) {
      String dirname = (args.length == 0) ? "studentdb" : args[0];
      SimpleDB db = new SimpleDB(dirname);
      Transaction tx = db.newTx();
      Planner planner = db.planner();

      Scanner sc = new Scanner(System.in);
      System.out.print("SQL> ");
      while (sc.hasNextLine()) {
         String cmd = sc.nextLine().trim();
         if (cmd.startsWith("exit"))
            break;
         else if (cmd.startsWith("select"))
            doQuery(planner, tx, cmd);
         else if (!cmd.isEmpty())
            doUpdate(planner, tx, cmd);
         System.out.print("\nSQL> ");
      }

      tx.commit();
      sc.close();
   }

   private static void doQuery(Planner planner, Transaction tx, String cmd) {
      try {
         Plan p = planner.createQueryPlan(cmd, tx);
         Scan s = p.open();
         Schema sch = p.schema();

         printHeader(sch);
         while (s.next()) {
            printRow(sch, s);
         }
         s.close();
      }
      catch (Exception e) {
         System.out.println("SQL Exception: " + e.getMessage());
      }
   }

   private static void doUpdate(Planner planner, Transaction tx, String cmd) {
      try {
         int howmany = planner.executeUpdate(cmd, tx);
         System.out.println(howmany + " records processed");
      }
      catch (Exception e) {
         System.out.println("SQL Exception: " + e.getMessage());
      }
   }

   private static void printHeader(Schema sch) {
      int totalwidth = 0;
      for (String fldname : sch.fields()) {
         int width = columnWidth(sch, fldname);
         totalwidth += width;
         System.out.format("%" + width + "s", fldname);
      }
      System.out.println();
      for (int i = 0; i < totalwidth; i++)
         System.out.print("-");
      System.out.println();
   }

   private static void printRow(Schema sch, Scan s) {
      for (String fldname : sch.fields()) {
         int width = columnWidth(sch, fldname);
         int fldtype = sch.type(fldname);
         if (fldtype == INTEGER)
            System.out.format("%" + width + "d", s.getInt(fldname));
         else
            System.out.format("%" + width + "s", s.getString(fldname));
      }
      System.out.println();
   }

   private static int columnWidth(Schema sch, String fldname) {
      int fldtype = sch.type(fldname);
      int fldlength = (fldtype == INTEGER) ? 6 : sch.length(fldname);
      return Math.max(fldname.length(), fldlength) + 1;
   }
}