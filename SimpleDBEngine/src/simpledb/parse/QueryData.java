package simpledb.parse;

import java.util.*;

import simpledb.query.*;

/**
 * Data for the SQL <i>select</i> statement.
 * @author Edward Sciore
 */
public class QueryData {
   private List<String> fields;
   private Collection<String> tables;
   private Predicate pred;
   private Map<String, Boolean> sortFields;
   
   /**
    * Saves the field and table list and predicate.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred, Map<String, Boolean> sortFields) {
      this.fields = fields;
      this.tables = tables;
      this.pred = pred;
       this.sortFields = sortFields;
   }
   
   /**
    * Returns the fields mentioned in the select clause.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
   }

   /**
    * Returns the fields to sort on mentioned in the select clause.
    * @return a map of sort field names and sorting order
    */
   public Map<String, Boolean> sortFields() {
      return sortFields;
   }
   
   /**
    * Returns the tables mentioned in the from clause.
    * @return a collection of table names
    */
   public Collection<String> tables() {
      return tables;
   }
   
   /**
    * Returns the predicate that describes which
    * records should be in the output table.
    * @return the query predicate
    */
   public Predicate pred() {
      return pred;
   }
   
   public String toString() {
      String result = "select ";
      for (String fldname : fields)
         result += fldname + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      result += " from ";
      for (String tblname : tables)
         result += tblname + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      String predstring = pred.toString();
      if (!predstring.equals(""))
         result += " where " + predstring;
      if (!sortFields.isEmpty()) {
         result += " order by ";
         for (var entry : sortFields.entrySet()) //(field, isAscending)
            result += entry.getKey() + (entry.getValue() ? " asc" : " desc") + ", ";
         result = result.substring(0, result.length()-2);
      }
      return result;
   }
}
