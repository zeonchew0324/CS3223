package simpledb.parse;
import java.util.Scanner;

// Will successfully read in lines of text denoting an
// SQL expression of the form "id opr c" or "c opr id",
// where opr is one of =, <, <=, <>, >, >=, or !=.

public class LexerTest {
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		while (sc.hasNext()) {
			String s = sc.nextLine();
			Lexer lex = new Lexer(s);
			String lhs = readOperand(lex);
			String opr = lex.eatOpr();
			String rhs = readOperand(lex);
			System.out.println(lhs + " " + opr + " " + rhs);
		}
		sc.close();
	}

	private static String readOperand(Lexer lex) {
		if (lex.matchId())
			return lex.eatId();
		if (lex.matchStringConstant())
			return "'" + lex.eatStringConstant() + "'";
		if (lex.matchIntConstant())
			return String.valueOf(lex.eatIntConstant());
		throw new BadSyntaxException();
	}
}
