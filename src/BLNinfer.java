import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.bayesnets.inference.BackwardSampling;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithChildren;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithPriors;
import edu.tum.cs.bayesnets.inference.SmileBackwardSampling;
import edu.tum.cs.bayesnets.inference.SmileEPIS;
import edu.tum.cs.bayesnets.relational.core.ABL;
import edu.tum.cs.bayesnets.relational.core.BLOGModel;
import edu.tum.cs.bayesnets.relational.core.bln.*;
import edu.tum.cs.bayesnets.relational.core.bln.py.BayesianLogicNetworkPy;
import edu.tum.cs.bayesnets.relational.inference.BNSampler;
import edu.tum.cs.bayesnets.relational.inference.CSPSampler;
import edu.tum.cs.bayesnets.relational.inference.GibbsSampling;
import edu.tum.cs.bayesnets.relational.inference.LikelihoodWeighting;
import edu.tum.cs.bayesnets.relational.inference.Sampler;
import edu.tum.cs.tools.Stopwatch;


public class BLNinfer {

	enum Algorithm {LikelihoodWeighting, CSP, GibbsSampling, EPIS, BackwardSampling, SmileBackwardSampling, BackwardSamplingPriors, Experimental};
	
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
			String[] cwPreds = null;
			boolean showBN = false;
			boolean usePython = false;
			boolean debug = false;
			boolean saveInstance = false;
			
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
				else if(args[i].equals("-si"))
					saveInstance = true;				
				else if(args[i].equals("-py"))
					usePython = true;				
				else if(args[i].equals("-cw"))
					cwPreds = args[++i].split(",");		
				else if(args[i].equals("-maxSteps"))
					maxSteps = Integer.parseInt(args[++i]);
				else if(args[i].equals("-lw"))
					algo = Algorithm.LikelihoodWeighting;
				else if(args[i].equals("-epis"))
					algo = Algorithm.EPIS;
				else if(args[i].equals("-csp"))
					algo = Algorithm.CSP;
				else if(args[i].equals("-gs"))
					algo = Algorithm.GibbsSampling;
				else if(args[i].equals("-bs"))
					algo = Algorithm.BackwardSampling;
				else if(args[i].equals("-sbs"))
					algo = Algorithm.SmileBackwardSampling;
				else if(args[i].equals("-bsp"))
					algo = Algorithm.BackwardSamplingPriors;
				else if(args[i].equals("-exp"))
					algo = Algorithm.Experimental;
				else if(args[i].equals("-debug"))
					debug = true;
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(bifFile == null || dbFile == null || blogFile == null || blnFile == null || query == null) {
				System.out.println("\n usage: inferBLN <-b <BLOG file>> <-x <xml-BIF file>> <-l <BLN file>> <-e <evidence db>> <-q <comma-sep. queries>> [options]\n\n"+
							         "    -maxSteps #      the maximum number of steps to take\n" + 
							         "    -lw              algorithm: likelihood weighting (default)\n" +
							         "    -gs              algorithm: Gibbs sampling\n" +						
							         //"    -csp             algorithm: CSP-based sampling\n" +
							         "    -bs              algorithm: backward sampling\n" +
							         "    -sbs             algorithm: SMILE backward sampling\n" +
							         "    -epis            algorithm: SMILE evidence prepropagation importance sampling\n" +
							         "    -py              use Python-based logic engine\n" +
							         "    -debug           debug mode with additional outputs\n" + 
							         "    -s           	   show ground network in editor\n" +
							         "    -si              save ground network instance in BIF format (.instance.xml)\n" +
							         "    -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n");
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

			// instantiate ground model
			ABL blog = new ABL(blogFile, bifFile);
			AbstractGroundBLN gbln;
			if(!usePython) {
				BayesianLogicNetwork bln = new BayesianLogicNetwork(blog, blnFile);
				gbln = new GroundBLN(bln, dbFile);
			}
			else {
				BayesianLogicNetworkPy bln = new BayesianLogicNetworkPy(blog, blnFile);
				gbln = new edu.tum.cs.bayesnets.relational.core.bln.py.GroundBLN(bln, dbFile);
			}
			if(cwPreds != null) {
				System.out.println("extending evidence...");
				for(String predName : cwPreds)
					gbln.getDatabase().setClosedWorldPred(predName);
			}
			gbln.instantiateGroundNetwork();
			if(showBN) {
				gbln.getGroundNetwork().show();
			}
			if(saveInstance) {
				String baseName = bifFile.substring(0, bifFile.lastIndexOf('.'));
				gbln.getGroundNetwork().saveXMLBIF(baseName + ".instance.xml");
			}
			
			if(false) {
				System.out.println("\ndomain:");
				gbln.getDatabase().printDomain(System.out);
				System.out.println();
			}			
			
			// run inference
			Stopwatch sw = new Stopwatch();
			sw.start();
			Sampler sampler = null;
			switch(algo) {
			case LikelihoodWeighting: 
				sampler = new LikelihoodWeighting(gbln); break;
			case CSP: 
				sampler = new CSPSampler(gbln); break;
			case GibbsSampling:	
				sampler = new GibbsSampling(gbln); break;
			case EPIS:
				sampler = new BNSampler(gbln, SmileEPIS.class); break;
			case SmileBackwardSampling:
				sampler = new BNSampler(gbln, SmileBackwardSampling.class); break;
			case BackwardSampling:
				sampler = new BNSampler(gbln, BackwardSampling.class); break;
			case BackwardSamplingPriors:
				sampler = new BNSampler(gbln, BackwardSamplingWithPriors.class); break;
			case Experimental:
				sampler = new BNSampler(gbln, BackwardSamplingWithChildren.class); break;
			}
			sampler.setDebugMode(debug);
			System.out.println("algorithm: " + sampler.getAlgorithmName());
			sampler.infer(queries.toArray(new String[0]), maxSteps, 100);
			sw.stop();
			System.out.println("total inference time: " + sw.getElapsedTimeSecs() + " seconds");
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
