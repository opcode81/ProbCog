import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.bayesnets.relational.core.BLOGModel;
import edu.tum.cs.bayesnets.relational.core.bln.BayesianLogicNetwork;
import edu.tum.cs.bayesnets.relational.inference.CSPSampler;
import edu.tum.cs.bayesnets.relational.inference.GibbsSampling;
import edu.tum.cs.bayesnets.relational.inference.GroundBLN;
import edu.tum.cs.bayesnets.relational.inference.LikelihoodWeighting;
import edu.tum.cs.tools.Stopwatch;


public class BLNinfer {

	enum Algorithm {LikelihoodWeighting, CSP, GibbsSampling};
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String blogFile = null;
			String bifFile = null;
			String blnFile = null;
			String dbFile = null;
			String query = null;
			int maxSteps = 1000;
			Algorithm algo = Algorithm.LikelihoodWeighting;
			boolean showBN = false;
			
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-b"))
					blogFile = args[++i];
				else if(args[i].equals("-x"))
					bifFile = args[++i];
				else if(args[i].equals("-l"))
					blnFile = args[++i];
				else if(args[i].equals("-q"))
					query = args[++i];
				else if(args[i].equals("-e"))
					dbFile = args[++i];				
				else if(args[i].equals("-s"))
					showBN = true;				
				else if(args[i].equals("-maxSteps"))
					maxSteps = Integer.parseInt(args[++i]);
				else if(args[i].equals("-lw"))
					algo = Algorithm.LikelihoodWeighting;
				else if(args[i].equals("-csp"))
					algo = Algorithm.CSP;
				else if(args[i].equals("-gs"))
					algo = Algorithm.GibbsSampling;
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(bifFile == null || dbFile == null || blogFile == null || blnFile == null || query == null) {
				System.out.println("\n usage: inferBLN <-b <BLOG file>> <-x <xml-BIF file>> <-l <BLN file>> <-e <evidence db>> <-q <comma-sep. queries>> [options]\n\n"+
							         "    -maxSteps #    the maximum number of steps to take\n" + 
							         "    -lw            algorithm: likelihood weighting\n" +
							         "    -csp           algorithm: CSP-based sampling");
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
				
			// run inference
			BayesianLogicNetwork bln = new BayesianLogicNetwork(new BLOGModel(blogFile, bifFile), blnFile);
			GroundBLN gbln = new GroundBLN(bln, dbFile);
			if(showBN)
				gbln.getGroundNetwork().show("/usr/wiss/jain/work/code/BNJ/plugins");
			Stopwatch sw = new Stopwatch();
			sw.start();
			switch(algo) {
			case LikelihoodWeighting:
				new LikelihoodWeighting(gbln).infer(queries.toArray(new String[0]), maxSteps, 100);
				break;
			case CSP:
				new CSPSampler(gbln).infer(queries.toArray(new String[0]), maxSteps, 100);
				break;
			case GibbsSampling:
				new GibbsSampling(gbln).infer(queries.toArray(new String[0]), maxSteps, 100);
				break;
			}				
			sw.stop();
			System.out.println("Inference time: " + sw.getElapsedTimeSecs() + " seconds");
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
