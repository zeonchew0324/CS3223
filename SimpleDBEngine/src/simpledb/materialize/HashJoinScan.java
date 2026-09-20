
package simpledb.materialize;

import java.util.*;
import simpledb.record.Schema;
import simpledb.query.*;

/**
 * The Scan class for the hash join. Walks the partition pairs in order;
 * for each pair it builds an in-memory hash table from the build partition
 * and probes it with every record of the probe partition.
 */
public class HashJoinScan implements Scan {
    private List<TempTable> buildparts, probeparts;
   private Schema buildsch, probesch;
   private String buildfld, probefld;

   private int current;
   private Map<Constant, List<Map<String, Constant>>> table;
   private Scan probescan = null;
   private List<Map<String, Constant>> matches = null;
   private int matchidx;
   private Map<String, Constant> buildrec;

   public HashJoinScan(List<TempTable> buildparts, List<TempTable> probeparts,
                       Schema buildsch, Schema probesch, String buildfld, String probefld) {
      this.buildparts = buildparts;
      this.probeparts = probeparts;
      this.buildsch = buildsch;
      this.probesch = probesch;
      this.buildfld = buildfld;
      this.probefld = probefld;
      beforeFirst();
   }

   public void beforeFirst() {
      if (probescan != null)
         probescan.close();
      probescan = null;
      current = -1;
      matches = null;
      nextPartition();
   }

   public boolean next() {
      while (true) {
         if (matches != null && matchidx < matches.size()) {
            buildrec = matches.get(matchidx++);
            return true;
         }
         if (probescan != null && probescan.next()) {
            matches = table.getOrDefault(probescan.getVal(probefld), Collections.emptyList());
            matchidx = 0;
            continue;
         }
         if (!nextPartition())
            return false;
      }
   }

   private boolean nextPartition() {
      current++;
      if (current >= buildparts.size())
         return false;
      if (probescan != null)
         probescan.close();
      table = new HashMap<>();
      Scan b = buildparts.get(current).open();
      while (b.next()) {
         Map<String, Constant> rec = new HashMap<>();
         for (String f : buildsch.fields())
            rec.put(f, b.getVal(f));
         table.computeIfAbsent(b.getVal(buildfld), key -> new ArrayList<>()).add(rec);
      }
      b.close();
      probescan = probeparts.get(current).open();
      matches = null;
      return true;
   }

   public Constant getVal(String fldname) {
      if (buildsch.hasField(fldname))
         return buildrec.get(fldname);
      else
         return probescan.getVal(fldname);
   }

   public int getInt(String fldname) {
      return getVal(fldname).asInt();
   }

   public String getString(String fldname) {
      return getVal(fldname).asString();
   }

   public boolean hasField(String fldname) {
      return buildsch.hasField(fldname) || probesch.hasField(fldname);
   }

   public void close() {
      if (probescan != null)
         probescan.close();
   }


}