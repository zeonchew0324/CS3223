package simpledb.test;

import java.util.Scanner;

import static java.sql.Types.INTEGER;

import simpledb.file.FileMgr;
import simpledb.opt.HeuristicQueryPlanner;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class SimpleIJ {
   /** When false, rows are counted but not printed, so timings exclude console output. */
   private static boolean printRows = true;

   public static void main(String[] args) {
      String dirname = (args.length == 0) ? "studentdb" : args[0];
      SimpleDB db = new SimpleDB(dirname);
      Transaction tx = db.newTx();
      Planner planner = db.planner();

      Scanner sc = new Scanner(System.in);
      System.out.print("SQL> ");
      while (sc.hasNextLine()) {
         String cmd = sc.nextLine().trim();
         // if (cmd.startsWith("exit"))
         //    break;
         // else if (cmd.startsWith("select"))
         //    doQuery(planner, tx, cmd);
         // else if (!cmd.isEmpty())
         //    doUpdate(planner, tx, cmd);
         if (cmd.equalsIgnoreCase("exit")) {
            break;
         } else if (cmd.toLowerCase().startsWith("explain")) {
            doExplain(planner, tx, cmd.substring("explain".length()).trim());
         } else if (cmd.toLowerCase().startsWith("set ")) {
            doSet(cmd);
         } else if (cmd.toLowerCase().startsWith("select")) {
            doQuery(planner, tx, cmd);
         } else if (!cmd.isEmpty()) {
            doUpdate(planner, tx, cmd);
         }
         System.out.print("\nSQL> ");
      }

      tx.commit();
      sc.close();
   }

   private static void doQuery(Planner planner, Transaction tx, String cmd) {
      try {
         long readsBefore = FileMgr.blocksRead;
         long t0 = System.nanoTime();
         Plan p = planner.createQueryPlan(cmd, tx);
         long t1 = System.nanoTime();

         Scan s = p.open();
         Schema sch = p.schema();
         if (printRows)
            printHeader(sch);
         int rows = 0;
         while (s.next()) {
            rows++;
            if (printRows)
               printRow(sch, s);
         }
         s.close();
         long t2 = System.nanoTime();

         System.out.format("%d rows | join=%s | plan %.1f ms | scan %.1f ms | est blocks %d | actual block reads %d%n",
               rows, HeuristicQueryPlanner.joinMethod,
               (t1 - t0) / 1e6, (t2 - t1) / 1e6,
               p.blocksAccessed(), FileMgr.blocksRead - readsBefore);
      }
      catch (Exception e) {
         // System.out.println("SQL Exception: " + e.getMessage());
         e.printStackTrace();
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

   /**
    * Session settings for experiments:
    *   set join auto|index|merge|hash|nested   restrict the planner's join method
    *   set print on|off                        print result rows, or only count them
    */
   private static void doSet(String cmd) {
      String[] parts = cmd.trim().toLowerCase().split("\\s+");
      if (parts.length == 3 && parts[1].equals("join")) {
         switch (parts[2]) {
            case "auto": case "index": case "merge": case "hash": case "nested":
               HeuristicQueryPlanner.joinMethod = parts[2];
               System.out.println("join method = " + parts[2]);
               return;
         }
      } else if (parts.length == 3 && parts[1].equals("print")) {
         printRows = parts[2].equals("on");
         System.out.println("print rows = " + printRows);
         return;
      }
      System.out.println("usage: set join auto|index|merge|hash|nested  or  set print on|off");
   }

   private static void doExplain(Planner planner, Transaction tx, String cmd) {
      try {
         Plan p = planner.createQueryPlan(cmd, tx);
         System.out.println("Query plan:");
         System.out.print(p.describePlan(""));
      } catch (Exception e) {
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
         Constant val = s.getVal(fldname);

         if (val.isNull()) {
            System.out.format("%" + width + "s", "NULL");
         } else if (sch.type(fldname) == INTEGER) {
            System.out.format("%" + width + "d", val.asInt());
         } else {
            System.out.format("%" + width + "s", val.asString());
         }
         // int fldtype = sch.type(fldname);
         // if (fldtype == INTEGER)
         //    System.out.format("%" + width + "d", s.getInt(fldname));
         // else
         //    System.out.format("%" + width + "s", s.getString(fldname));
      }
      System.out.println();
   }

   private static int columnWidth(Schema sch, String fldname) {
      int fldtype = sch.type(fldname);
      int fldlength = (fldtype == INTEGER) ? 6 : sch.length(fldname);
      return Math.max(fldname.length(), fldlength) + 1;
   }
}