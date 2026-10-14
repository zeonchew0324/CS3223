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
   private List<AggregateData> aggregates;
   private List<String> groupFields;
   
   /**
    * Saves the field and table list and predicate.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred, Map<String, Boolean> sortFields, List<AggregateData> aggregates, List<String> groupFields) {
      this.fields = fields;
      this.tables = tables;
      this.pred = pred;
      this.sortFields = sortFields;
      this.aggregates = aggregates;
      this.groupFields = groupFields;
   }
   
   /**
    * Returns the fields mentioned in the select clause.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
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

   public Map<String, Boolean> sortFields() {
      return sortFields;
   }

   public List<AggregateData> aggregates() {
      return aggregates;
   }

   public List<String> groupFields() {
      return groupFields;
   }
   
   public String toString() {
      String result = "select ";

      List<String> selectItems = new ArrayList<>();

      for (String fldname : fields) {
         selectItems.add(fldname);
      }

      for (AggregateData agg : aggregates) {
         selectItems.add(agg.function() + "(" + agg.field() + ")");
      }

      result += String.join(", ", selectItems);
      result += " from " + String.join(", ", tables);

      String predstring = pred.toString();
      if (!predstring.equals("")) {
         result += " where " + predstring;
      }

      if (!groupFields.isEmpty()) {
         result += " group by " + String.join(", ", groupFields);
      }

      if (!sortFields.isEmpty()) {
         result += " order by ";

         boolean first = true;

         for (Map.Entry<String, Boolean> entry : sortFields.entrySet()) {
            if (!first) {
               result += ", ";
            }

            result += entry.getKey();
            result += entry.getValue() ? " asc" : " desc";
            first = false;
         }
      }

      return result;
   }
}
