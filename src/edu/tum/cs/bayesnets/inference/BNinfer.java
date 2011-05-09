package edu.tum.cs.bayesnets.inference;
import java.io.File;
import java.util.Arrays;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BNDatabase;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.inference.BasicSampledDistribution;
import edu.tum.cs.inference.GeneralSampledDistribution;
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
			boolean useMaxSteps = false;
			String outputDistFile = null, referenceDistFile = null;
			
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
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(networkFile == null || dbFile == null || query == null) {
				System.out.println("\n usage: BNinfer <arguments>\n\n" +
						             "   required arguments:\n\n" +
						             "     -n <network file>         fragment network (XML-BIF or PMML)\n" + 
						             "     -e <evidence db pattern>  an evidence database file or file mask\n" +
						             "     -q <comma-sep. queries>   queries (predicate names or partially grounded terms with lower-case vars)\n\n" +
						             "   options:\n\n" +
									 "     -maxSteps #      the maximum number of steps to take, where applicable (default: 1000)\n" +
									 "     -maxTrials #     the maximum number of trials per step for BN sampling algorithms (default: 5000)\n" +
									 "     -infoInterval #  the number of steps after which to output a status message\n" +
									 "     -skipFailedSteps failed steps (> max trials) should just be skipped\n\n" +	
									 "     -t [secs]        use time-limited inference (default: 10 seconds)\n" +
									 "     -infoTime #      interval in secs after which to display intermediate results (time-limited inference, default: 1.0)\n" +
									 "     -ia <name>       inference algorithm selection; valid names:");
				for(Algorithm a : Algorithm.values()) 
					System.out.printf("                        %-28s  %s\n", a.toString(), a.getDescription());				
				System.out.println(
							         "     -od <file>       save output distribution to file\n" +
							         "     -cd <file>       compare results of inference to reference distribution in file\n" + 
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
			int[] evidenceDomainIndices = new int[nodes.length];
			Arrays.fill(evidenceDomainIndices, -1);
			BNDatabase db = new BNDatabase(new File(dbFile));
			for(Entry<String,String> entry : db.getEntries()) {
				BeliefNode node = bn.getNode(entry.getKey());
				if(node == null)
					throw new Exception("Evidence node '" + entry.getKey() + "' not found in model.");
				Discrete dom = (Discrete)node.getDomain();
				int domidx = dom.findName(entry.getValue());
				if(domidx == -1)
					throw new Exception("Value '" + entry.getValue() + "' not found in domain of node '" + entry.getKey() + "'");
				evidenceDomainIndices[bn.getNodeIndex(node)] = domidx;
			}
			
			// read reference distribution if any
			GeneralSampledDistribution referenceDist = null;
			if(referenceDistFile != null) {
				referenceDist = GeneralSampledDistribution.fromFile(new File(referenceDistFile));
			}
			
			// determine queries
			Vector<Integer> queryVars = new Vector<Integer>();
			for(String qq : queries) {
				int varIdx = bn.getNodeIndex(qq);
				if(varIdx == -1)
					throw new Exception("Unknown variable '" + qq + "'");
				queryVars.add(varIdx);
			}
			
			// run inference
			Stopwatch sw = new Stopwatch();
			sw.start();
			// - create sampler 
			Sampler sampler = algo.createSampler(bn);
			// - set evidence and options
			sampler.setEvidence(evidenceDomainIndices);
			sampler.setQueryVars(queryVars);
			sampler.setDebugMode(debug);
			sampler.setMaxTrials(maxTrials);
			sampler.setSkipFailedSteps(skipFailedSteps);
			sampler.setNumSamples(maxSteps);
			sampler.setInfoInterval(infoInterval);
			// - run inference
			SampledDistribution dist = null; 
			if(timeLimitedInference) {
				if(!(sampler instanceof ITimeLimitedInference)) 
					throw new Exception(sampler.getAlgorithmName() + " does not support time-limited inference");					
				ITimeLimitedInference tliSampler = (ITimeLimitedInference) sampler;
				if(!useMaxSteps)				
					sampler.setNumSamples(Integer.MAX_VALUE);
				sampler.setInfoInterval(Integer.MAX_VALUE); // provide intermediate results only triggered by time-limited inference
				TimeLimitedInference tli = new TimeLimitedInference(tliSampler, timeLimit, infoIntervalTime);
				tli.setReferenceDistribution(referenceDist);
				dist = tli.run();
				if(referenceDist != null)
					System.out.println("MSEs: " + tli.getMSEs());				
			}
			else				
				dist = sampler.infer();			
			sw.stop();
			
			// print results
			for(String qq : queries) {
				int varIdx = bn.getNodeIndex(qq);
				dist.printVariableDistribution(System.out, varIdx);
			}
			
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
				BasicSampledDistribution.compareDistributions(dist, referenceDist, evidenceDomainIndices);
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
}
