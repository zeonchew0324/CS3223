package simpledb.parse;
import java.util.Scanner;

public class ParserTest {
   public static void main(String[] args) {
      Scanner sc = new Scanner(System.in);
      System.out.print("Enter an SQL statement: ");
      while (sc.hasNextLine()) {
         String s = sc.nextLine().trim();
         if (s.isEmpty()) {
            System.out.print("Enter an SQL statement: ");
            continue;
         }
         Parser p = new Parser(s);
         try {
            if (s.startsWith("select"))
               p.query();
            else
               p.updateCmd();
            System.out.println("yes: " + s);
         }
         catch (BadSyntaxException ex) {
            System.out.println("no: " + s);
         }
         System.out.print("Enter an SQL statement: ");
      }
      sc.close();
   }
}


