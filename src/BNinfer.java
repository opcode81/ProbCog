import java.util.Vector;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.Algorithm;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.bayesnets.inference.Sampler;
import edu.tum.cs.util.Stopwatch;


public class BNinfer {
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String networkFile = null;
			String dbFile = null;
			String query = null;
			int maxSteps = 1000;
			int maxTrials = 5000;
			int infoInterval = 100;
			Algorithm algo = Algorithm.LikelihoodWeighting;
			boolean debug = false;
			boolean skipFailedSteps = false;
			boolean removeDeterministicCPTEntries = false;
			double timeLimit = 10.0, infoIntervalTime = 1.0;
			boolean timeLimitedInference = false;
			
			// read arguments
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-n"))
					networkFile = args[++i];
				else if(args[i].equals("-q"))
					query = args[++i];
				else if(args[i].equals("-e"))
					dbFile = args[++i];				
				else if(args[i].equals("-nodetcpt"))
					removeDeterministicCPTEntries = true;				
				else if(args[i].equals("-skipFailedSteps"))
					skipFailedSteps = true;				
				else if(args[i].equals("-maxSteps"))
					maxSteps = Integer.parseInt(args[++i]);
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
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(networkFile == null || dbFile == null || query == null) {
				System.out.println("\n usage: BLNinfer <arguments>\n\n" +
						             "   required arguments:\n\n" +
						             "     -n <network file>         fragment network (XML-BIF or PMML)\n" + 
						             "     -e <evidence db pattern>  an evidence database file or file mask\n" +
						             "     -q <comma-sep. queries>   queries (predicate names or partially grounded terms with lower-case vars)\n\n" +
						             "   options:\n\n" +
									 "     -maxSteps #      the maximum number of steps to take (default: 1000)\n" +
									 "     -maxTrials #     the maximum number of trials per step for BN sampling algorithms (default: 5000)\n" +
									 "     -infoInterval #  the number of steps after which to output a status message\n" +
									 "     -skipFailedSteps failed steps (> max trials) should just be skipped\n\n" +	
									 "     -t [secs]        use time-limited inference (default: 10 seconds)\n" +
									 "     -infoTime #      interval in secs after which to display intermediate results (time-limited inference, default: 1.0)\n" +
									 "     -ia <name>       inference algorithm selection; valid names:");
				for(Algorithm a : Algorithm.values()) 
					System.out.printf("                        %-28s  %s\n", a.toString(), a.getDescription());				
				System.out.println(
							         "     -debug           debug mode with additional outputs\n" + 
							         "     -nodetcpt        remove deterministic CPT columns by replacing 0s with low prob. values\n");
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

			// load model
			BeliefNetworkEx bn = new BeliefNetworkEx(networkFile);
			BeliefNode[] nodes = bn.bn.getNodes();
			
			// (on request) remove deterministic dependencies in CPTs
			if(removeDeterministicCPTEntries) {
				final double lowProb = 0.001; 
				for(BeliefNode node : nodes) {
					CPF cpf = node.getCPF();					
					for(int i = 0; i < cpf.size(); i++)
						if(cpf.getDouble(i) == 0.0)
							cpf.put(i, new ValueDouble(lowProb));
					cpf.normalizeByDomain();
				}
			}
			
			// read evidence database
			/*Database db = new Database(blog);
			db.readBLOGDB(dbFile);
			if(cwPreds != null) {
				for(String predName : cwPreds)
					db.setClosedWorldPred(predName);
			}*/
			int[] evidenceDomainIndices = new int[nodes.length];
			
			// run inference
			Stopwatch sw = new Stopwatch();
			sw.start();
			// - create sampler 
			Sampler sampler = algo.createSampler(bn);
			// - set options
			sampler.setDebugMode(debug);
			sampler.setMaxTrials(maxTrials);
			sampler.setSkipFailedSteps(skipFailedSteps);
			sampler.setNumSamples(maxSteps);
			sampler.setInfoInterval(infoInterval);
			// - run inference
			SampledDistribution dist = null; 
			if(timeLimitedInference) {
				/*sampler.setNumSamples(Integer.MAX_VALUE);
				TimeLimitedInference tli = new TimeLimitedInference(sampler, queries, timeLimit, infoIntervalTime);
				results = tli.run();*/				
			}
			else				
				dist = sampler.infer(evidenceDomainIndices);			
			sw.stop();
			
			// print results
			dist.print(System.out);
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
