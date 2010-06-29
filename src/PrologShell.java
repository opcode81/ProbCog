

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.prolog.PrologKnowledgeBase;

/**
 * @author jain
 */
public class PrologShell {

	public static void main(String[] args) {
		System.out.println("\n"+
				"The Simple yProlog Interactive Shell\n\n" +
				"usage:\n" +
				"      tell:  parent(eve, abel).\n" +
				"             female(eve).\n" +
				"             mother(X,Y) :- parent(X,Y), female(X).\n" +
				"       ask:  mother(eve, abel)\n" +
				"             mother(eve, X)\n" +
				"   consult:  consult myfile.pl\n" +
				"      exit:  exit\n"
			);
		
		PrologKnowledgeBase kb = new PrologKnowledgeBase();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String constant = "\\s*([a-z]\\w*|[0-9]+)\\s*";
		Pattern patGroundAtom = Pattern.compile(String.format("\\w+\\(%s(,%s)*\\)", constant, constant));
		for(;;) {
			
			try {
				// get input query from stdin
				System.out.print("\n> ");
				String input = br.readLine().trim();

				if(input.equalsIgnoreCase("exit"))
					break;

				// parse expression...
				if(input.startsWith("consult")) {
					String filename = input.substring(7).trim();
					System.out.printf("consulting %s\n", filename);
					kb.consultFile(filename);
				}				
				else if(input.endsWith("."))
					kb.tell(input);
				else {	
					Matcher m = patGroundAtom.matcher(input);
					if(m.matches()) {
						boolean result = kb.ask(input);
						System.out.println(result ? "Yes" : "No");
					}
					else {
						for(String atom : kb.fetchAtoms(input)) {
							System.out.println(atom);
						}						
					}
				}
			}
			catch (IOException e) {
				System.err.println(e.getMessage());
			}
			catch (java.lang.Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}
}
