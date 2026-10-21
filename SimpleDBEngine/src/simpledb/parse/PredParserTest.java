package simpledb.parse;
import java.util.Scanner;

public class PredParserTest {
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		System.out.print("Enter an SQL predicate: ");
		while (sc.hasNextLine()) {
			String s = sc.nextLine().trim();
			if (s.isEmpty()) {
				System.out.print("Enter an SQL predicate: ");
				continue;
			}
			PredParser p = new PredParser(s);
			try {
				p.predicate();
				System.out.println("yes: " + s);
			}
			catch (BadSyntaxException ex) {
				System.out.println("no: " + s);
			}
			System.out.print("Enter an SQL predicate: ");
		}
		sc.close();
	}
}


