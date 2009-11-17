import java.io.File;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.inference.ITimeLimitedInference;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.inference.BasicSampledDistribution;
import edu.tum.cs.inference.GeneralSampledDistribution;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.ABL;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.srl.bayesnets.bln.py.BayesianLogicNetworkPy;
import edu.tum.cs.srl.bayesnets.inference.Algorithm;
import edu.tum.cs.srl.bayesnets.inference.BNSampler;
import edu.tum.cs.srl.bayesnets.inference.Sampler;
import edu.tum.cs.srl.bayesnets.inference.TimeLimitedInference;
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
			boolean useMaxSteps = false;
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
			double timeLimit = 10.0, infoIntervalTime = 1.0;
			boolean timeLimitedInference = false;
			String outputDistFile = null, referenceDistFile = null;
			HashMap<String,String> params = new HashMap<String,String>();
			
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
				else if(args[i].equals("-maxSteps")) {
					maxSteps = Integer.parseInt(args[++i]);
					useMaxSteps = true;
				}
				else if(args[i].equals("-maxTrials"))
					maxTrials = Integer.parseInt(args[++i]);
				else if(args[i].equals("-ia")) {
					try {
						algo = Algorithm.valueOf(args[++i]);
					}
					catch(IllegalArgumentException e) {
						System.err.println("Error: Unknown inference algorithm '" + args[i] + "'");
						System.exit(1);
					}
				}
				else if(args[i].equals("-infoInterval"))
					infoInterval = Integer.parseInt(args[++i]);
				else if(args[i].equals("-debug"))
					debug = true;
				else if(args[i].equals("-t")) {
					timeLimitedInference = true;
					if(i+1 < args.length && !args[i+1].startsWith("-"))
						timeLimit = Double.parseDouble(args[++i]);					
				}
				else if(args[i].equals("-od"))
					outputDistFile = args[++i];	
				else if(args[i].equals("-cd"))
					referenceDistFile = args[++i];
				else if(args[i].startsWith("-p")) { // algorithm-specific parameter
					String[] pair = args[i].substring(2).split("=");
					if(pair.length != 2)
						throw new Exception("Argument '" + args[i] + "' for algorithm-specific parameterization is incorrectly formatted.");
					params.put(pair[0], pair[1]);
				}
				else
					throw new Exception("Unknown option " + args[i]);
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
									 "     -maxSteps #      the maximum number of steps to take (default: 1000 for non-time-limited inf.)\n" +
									 "     -maxTrials #     the maximum number of trials per step for BN sampling algorithms (default: 5000)\n" +
									 "     -infoInterval #  the number of steps after which to output a status message\n" +
									 "     -skipFailedSteps failed steps (> max trials) should just be skipped\n\n" +	
									 "     -t [secs]        use time-limited inference (default: 10 seconds)\n" +
									 "     -infoTime #      interval in secs after which to display intermediate results (time-limited inference, default: 1.0)\n" +
									 "     -ia <name>       inference algorithm selection; valid names:");
				for(Algorithm a : Algorithm.values()) 
					System.out.printf("                        %-28s  %s\n", a.toString(), a.getDescription());				
				System.out.println(
									 "     -p<key>=<value>  set algorithm-specific parameter\n" +
							         "     -debug           debug mode with additional outputs\n" + 
							         "     -s               show ground network in editor\n" +
							         "     -si              save ground network instance in BIF format (.instance.xml)\n" +
							         "     -nodetcpt        remove deterministic CPT columns by replacing 0s with low prob. values\n" +
							         "     -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n" +
							         "     -od <file>       save output distribution to file\n" +
							         "     -cd <file>       compare results of inference to reference distribution in file\n" + 
							         "     -py              use Python-based logic engine [deprecated]\n");
				System.exit(1);
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
			
			// read reference distribution if any
			GeneralSampledDistribution referenceDist = null;
			if(referenceDistFile != null) {
				referenceDist = GeneralSampledDistribution.fromFile(new File(referenceDistFile));
			}
			
			// run inference
			Stopwatch sw = new Stopwatch();
			sw.start();
			// - create sampler 
			Sampler sampler = algo.createSampler(gbln);
			// - set options
			sampler.setDebugMode(debug);
			if(sampler instanceof BNSampler) {
				((BNSampler)sampler).setMaxTrials(maxTrials);
				((BNSampler)sampler).setSkipFailedSteps(skipFailedSteps);
			}
			sampler.setNumSamples(maxSteps);
			sampler.setInfoInterval(infoInterval);
			sampler.handleParams(params);
			// - run inference
			SampledDistribution dist;
			if(timeLimitedInference) {
				if(!(sampler instanceof ITimeLimitedInference)) 
					throw new Exception(sampler.getAlgorithmName() + " does not support time-limited inference");					
				ITimeLimitedInference tliSampler = (ITimeLimitedInference) sampler;
				if(!useMaxSteps)				
					sampler.setNumSamples(Integer.MAX_VALUE);
				sampler.setInfoInterval(Integer.MAX_VALUE); // provide intermediate results only triggered by time-limited inference
				TimeLimitedInference tli = new TimeLimitedInference(tliSampler, queries, timeLimit, infoIntervalTime);
				tli.setReferenceDistribution(referenceDist);
				dist = tli.run();
				if(referenceDist != null)
					System.out.println("MSEs: " + tli.getMSEs());				
			}
			else {
				dist = sampler.infer();
			}
			sw.stop();
			
			// print results
			Sampler.printResults(dist, queries);
			
			// save output distribution
			if(outputDistFile != null) {
				GeneralSampledDistribution gdist = dist.toGeneralDistribution();
				File f=  new File(outputDistFile);
				gdist.write(f);		
				GeneralSampledDistribution gdist2 = GeneralSampledDistribution.fromFile(f);
				gdist2.print(System.out);
			}
			
			// compare distributions
			if(referenceDist != null) {				
				System.out.println("comparing to reference distribution...");
				compareDistributions(referenceDist, dist);
			}
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
	
	public static void compareDistributions(BasicSampledDistribution d1, BasicSampledDistribution d2) throws Exception {
		double mse = d1.getMSE(d2);
		System.out.println("MSE: " + mse);
	}
}
