import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.sat.MAPMaxWalkSAT;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.srl.mln.WeightedClausalKB;
import edu.tum.cs.tools.Stopwatch;

/**
 * MLN command-line inference tool 
 * @author jain
 */
public class MLNinfer {

	enum Algorithm {MaxWalkSAT};
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String mlnFile = null;
			String dbFile = null;
			String query = null;
			int maxSteps = 1000;
			Algorithm algo = Algorithm.MaxWalkSAT;
			String[] cwPreds = null;
			boolean debug = false;
			
			// read arguments
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-i"))
					mlnFile = args[++i];
				else if(args[i].equals("-q"))
					query = args[++i];
				else if(args[i].equals("-e"))
					dbFile = args[++i];				
				else if(args[i].equals("-cw"))
					cwPreds = args[++i].split(",");		
				else if(args[i].equals("-maxSteps"))
					maxSteps = Integer.parseInt(args[++i]);
				else if(args[i].equals("-mws"))
					algo = Algorithm.MaxWalkSAT;
				else if(args[i].equals("-debug"))
					debug = true;
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(mlnFile == null || dbFile == null || query == null) {
				System.out.println("\n usage: MLNinfer <-i <MLN file>> <-e <evidence db file>> <-q <comma-sep. queries>> [options]\n\n"+
									 "    -maxSteps #      the maximum number of steps to take [default: 1000]\n" +
							         "    -mws             algorithm: MaxWalkSAT (MAP inference) [default]\n" 
//							         "    -debug           debug mode with additional outputs\n" + 
//							         "    -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n"
									 );
				return;
			}			

			// determine queries
			Pattern comma = Pattern.compile("\\s*,\\s*");
			String[] candQueries = comma.split(query);
			Vector<String> queries = new Vector<String>();
			String q = "";
			for(int i = 0; i < candQueries.length; i++) {
				if(!q.equals(""))
					q += ",";
				q += candQueries[i];
				if(balancedParentheses(q)) {
					queries.add(q);
					q = "";
				}
			}
			if(!q.equals(""))
				throw new IllegalArgumentException("Unbalanced parentheses in queries");

			// load relational model
			MarkovLogicNetwork mln = new MarkovLogicNetwork(mlnFile);
			
			// instantiate ground model
			MarkovRandomField mrf = mln.groundMLN(dbFile);
			
			// run inference
			Stopwatch sw = new Stopwatch();
			sw.start();
	        WeightedClausalKB wckb = new WeightedClausalKB(mrf);
	        PossibleWorld state = new PossibleWorld(mln.getWorldVariables());
	        MAPMaxWalkSAT infer = new MAPMaxWalkSAT(wckb, state, mln.getWorldVariables(), mrf.getDb(), mln.getMaxWeight());
	        infer.setMaxSteps(maxSteps);
	        infer.run();
	        sw.stop();
			sw.stop();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean balancedParentheses(String s) {
		int n = 0;
		for(int i = 0; i < s.length(); i++) {
			if(s.charAt(i) == '(')
				n++;
			else if(s.charAt(i) == ')')
				n--;
		}
		return n == 0;
	}
}
