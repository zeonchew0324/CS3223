package simpledb.query;

/**
 * The class that denotes values stored in the database.
 * @author Edward Sciore
 */
public class Constant implements Comparable<Constant> {
   private Integer ival = null;
   private String  sval = null;
   
   public Constant(Integer ival) {
      this.ival = ival;
   }
   
   public Constant(String sval) {
      this.sval = sval;
   }

   public Constant() {
   }

   public boolean isNull() {
      return ival == null && sval == null;
   }
   
   public int asInt() {
      if (isNull()) {
         throw new IllegalStateException("NULL has no integer value");
      }
      return ival;
   }
   
   public String asString() {
      if (isNull()) {
         throw new IllegalStateException("NULL has no string value");
      }
      return sval;
   }
   
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      
      if (!(obj instanceof Constant)) {
         return false;
      }

      Constant c = (Constant) obj;

      if (isNull() || c.isNull()) {
         return isNull() && c.isNull();
      }

      return (ival != null) ? ival.equals(c.ival) : sval.equals(c.sval);
   }
   
   public int compareTo(Constant c) {
      if (isNull()) {
         return c.isNull() ? 0 : -1;
      }

      if (c.isNull()) {
         return 1;
      }

      return (ival != null) ? ival.compareTo(c.ival) : sval.compareTo(c.sval);
   }
   
   public int hashCode() {
      if (isNull()) {
         return 0;
      }
      
      return (ival != null) ? ival.hashCode() : sval.hashCode();
   }
   
   public String toString() {
      if (isNull()) {
         return "NULL";
      }
      
      return (ival != null) ? ival.toString() : sval.toString();
   }   
}
