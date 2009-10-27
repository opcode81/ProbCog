import java.util.Vector;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.ABL;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.srl.bayesnets.bln.py.BayesianLogicNetworkPy;
import edu.tum.cs.srl.bayesnets.inference.BLNInferenceFactory;
import edu.tum.cs.srl.bayesnets.inference.BNSampler;
import edu.tum.cs.srl.bayesnets.inference.InferenceResult;
import edu.tum.cs.srl.bayesnets.inference.Sampler;
import edu.tum.cs.srl.bayesnets.inference.BLNInferenceFactory.Algorithm;
import edu.tum.cs.util.Stopwatch;


public class BLNinfer {
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/*for(int i = 0; i < args.length; i++) {
			System.out.printf("%d %s\n", i, args[i]);
		}*/
		
		try {
			String declsFile = null;
			String networkFile = null;
			String logicFile = null;
			String dbFile = null;
			String query = null;
			int maxSteps = 1000;
			int maxTrials = 5000;
			int infoInterval = 100;
			Algorithm algo = Algorithm.LikelihoodWeighting;
			String[] cwPreds = null;
			boolean showBN = false;
			boolean usePython = false;
			boolean debug = false;
			boolean saveInstance = false;
			boolean skipFailedSteps = false;
			boolean removeDeterministicCPTEntries = false;
			
			// read arguments
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-b"))
					declsFile = args[++i];
				else if(args[i].equals("-x"))
					networkFile = args[++i];
				else if(args[i].equals("-l"))
					logicFile = args[++i];
				else if(args[i].equals("-q"))
					query = args[++i];
				else if(args[i].equals("-e"))
					dbFile = args[++i];				
				else if(args[i].equals("-s"))
					showBN = true;				
				else if(args[i].equals("-nodetcpt"))
					removeDeterministicCPTEntries = true;				
				else if(args[i].equals("-si"))
					saveInstance = true;				
				else if(args[i].equals("-skipFailedSteps"))
					skipFailedSteps = true;				
				else if(args[i].equals("-py"))
					usePython = true;				
				else if(args[i].equals("-cw"))
					cwPreds = args[++i].split(",");		
				else if(args[i].equals("-maxSteps"))
					maxSteps = Integer.parseInt(args[++i]);
				else if(args[i].equals("-maxTrials"))
					maxTrials = Integer.parseInt(args[++i]);
				else if(args[i].equals("-ia")) 
					algo = Algorithm.valueOf(args[++i]);				
				else if(args[i].equals("-infoInterval"))
					infoInterval = Integer.parseInt(args[++i]);
				else if(args[i].equals("-lw"))
					algo = Algorithm.LikelihoodWeighting;
				else if(args[i].equals("-lwu"))
					algo = Algorithm.LWU;
				else if(args[i].equals("-epis"))
					algo = Algorithm.EPIS;
				else if(args[i].equals("-gs"))
					algo = Algorithm.GibbsSampling;
				else if(args[i].equals("-bs"))
					algo = Algorithm.BackwardSampling;
				else if(args[i].equals("-sbs"))
					algo = Algorithm.SmileBackwardSampling;
				else if(args[i].equals("-bsp"))
					algo = Algorithm.BackwardSamplingPriors;
				else if(args[i].equals("-bsc"))
					algo = Algorithm.BackwardSamplingWithChildren;
				else if(args[i].equals("-lbs"))
					algo = Algorithm.LiftedBackwardSampling;
				else if(args[i].equals("-exp"))
					algo = Algorithm.Experimental;
				else if(args[i].equals("-satis"))
					algo = Algorithm.SATIS;
				else if(args[i].equals("-ea"))
					algo = Algorithm.EnumerationAsk;
				else if(args[i].equals("-satisex"))
					algo = Algorithm.SATISEx;
				else if(args[i].equals("-satisexg"))
					algo = Algorithm.SATISExGibbs;
				else if(args[i].equals("-mcsat"))
					algo = Algorithm.MCSAT;
				else if(args[i].equals("-pearl"))
					algo = Algorithm.Pearl;
				else if(args[i].equals("-elim"))
					algo = Algorithm.VarElim;
				else if(args[i].equals("-debug"))
					debug = true;
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(networkFile == null || dbFile == null || declsFile == null || logicFile == null || query == null) {
				System.out.println("\n usage: BLNinfer <arguments>\n\n" +
						             "   required arguments:\n\n" +
						             "     -b <declarations file>    declarations file (types, domains, signatures, etc.)\n" +
						             "     -x <network file>         fragment network (XML-BIF or PMML)\n" + 
						             "     -l <logic file>           logical constraints file\n" + 
						             "     -e <evidence db pattern>  an evidence database file or file mask\n" +
						             "     -q <comma-sep. queries>   queries (predicate names or partially grounded terms with lower-case vars)\n\n" +
						             "   options:\n\n" +
									 "     -maxSteps #      the maximum number of steps to take\n" +
									 "     -maxTrials #     the maximum number of trials per step for BN sampling algorithms\n" +
									 "     -infoInterval #  the number of steps after which to output a status message\n" +
									 "     -skipFailedSteps failed steps (> max trials) should just be skipped\n\n" +									 
									 "     -lw              algorithm: likelihood weighting (default)\n" +
									 "     -lwu             algorithm: likelihood weighting with uncertain evidence\n" +
							         "     -gs              algorithm: Gibbs sampling\n" +						
							         "     -exp             algorithm: Experimental\n" +
							         "     -satis           algorithm: SAT-IS\n" +
							         "     -satisex         algorithm: SAT-IS Extended (adds hard CPT constraints to the KB) \n" +
							         "     -satisexg        algorithm: SAT-IS Extended with interspersed Gibbs sampling\n" +
							         "     -bs              algorithm: backward sampling\n" +
							         "     -bsp             algorithm: backward sampling with priors\n" +
							         "     -bsc             algorithm: backward sampling with priors and extended context\n" +
							         "     -lbs             algorithm: lifted backward sampling\n" +
							         "     -sbs             algorithm: SMILE backward sampling\n" +
							         "     -epis            algorithm: SMILE evidence prepropagation importance sampling\n" +
							         "     -ea              algorithm: Enumeration-Ask (exact)\n" +
							         "     -pearl           algorithm: Pearl's algorithm for polytrees (exact)\n" +
							         "     -elim            algorithm: variable elimination (exact)\n" +
							         "     -mcsat           algorithm: MC-SAT\n\n" +
							         "     -ia <name>       inference algorithm selection; valid names:");
				for(Algorithm a : Algorithm.values()) 
					System.out.printf("                        %-28s  %s\n", a.toString(), a.getDescription());				
				System.out.println(
							         "     -py              use Python-based logic engine\n" +
							         "     -debug           debug mode with additional outputs\n" + 
							         "     -s               show ground network in editor\n" +
							         "     -si              save ground network instance in BIF format (.instance.xml)\n" +
							         "     -nodetcpt        remove deterministic CPT columns by replacing 0s with low prob. values\n" +
							         "     -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n");
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
			ABL blog = new ABL(declsFile, networkFile);
			
			// (on request) remove deterministic dependencies in CPTs
			if(removeDeterministicCPTEntries) {
				final double lowProb = 0.001; 
				for(BeliefNode node : blog.bn.getNodes()) {
					CPF cpf = node.getCPF();					
					for(int i = 0; i < cpf.size(); i++)
						if(cpf.getDouble(i) == 0.0)
							cpf.put(i, new ValueDouble(lowProb));
					cpf.normalizeByDomain();
				}
			}
			
			// read evidence database
			Database db = new Database(blog);
			db.readBLOGDB(dbFile);
			if(cwPreds != null) {
				for(String predName : cwPreds)
					db.setClosedWorldPred(predName);
			}
			
			// instantiate ground model
			AbstractGroundBLN gbln;
			if(!usePython) {
				BayesianLogicNetwork bln = new BayesianLogicNetwork(blog, logicFile);
				gbln = new GroundBLN(bln, db);
			}
			else {
				BayesianLogicNetworkPy bln = new BayesianLogicNetworkPy(blog, logicFile);
				gbln = new edu.tum.cs.srl.bayesnets.bln.py.GroundBLN(bln, db);
			}
			gbln.setDebugMode(debug);
			gbln.instantiateGroundNetwork();
			if(showBN) {
				gbln.getGroundNetwork().show();
			}
			if(saveInstance) {
				String baseName = networkFile.substring(0, networkFile.lastIndexOf('.'));
				gbln.getGroundNetwork().saveXMLBIF(baseName + ".instance.xml");
			}
			
			// run inference
			Stopwatch sw = new Stopwatch();
			sw.start();
			Sampler sampler = BLNInferenceFactory.createSampler(algo, gbln);
			sampler.setDebugMode(debug);
			if(sampler instanceof BNSampler) {
				((BNSampler)sampler).setMaxTrials(maxTrials);
				((BNSampler)sampler).setSkipFailedSteps(skipFailedSteps);
			}
			Vector<InferenceResult> results = sampler.infer(queries, maxSteps, infoInterval);
			sw.stop();
			for(InferenceResult res : results)
				res.print();
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
