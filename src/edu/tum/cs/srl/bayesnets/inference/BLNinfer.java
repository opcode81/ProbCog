package edu.tum.cs.srl.bayesnets.inference;
import java.io.File;
import java.util.Collection;
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
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.inference.BasicSampledDistribution.DistributionComparison;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.ABL;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.srl.bayesnets.bln.py.BayesianLogicNetworkPy;
import edu.tum.cs.util.Stopwatch;

/**
 * BLN inference tool
 * @author jain
 */
public class BLNinfer {
	
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
	boolean resultsFilterEvidence = false;
	double timeLimit = 10.0, infoIntervalTime = 1.0;
	boolean timeLimitedInference = false;
	String outputDistFile = null, referenceDistFile = null;
	HashMap<String,String> params = new HashMap<String,String>();
	
	// computed stuff
	Collection<InferenceResult> results;
	double samplingTime;
	int stepsTaken;

	public void readArgs(String[] args) throws Exception {
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
			else if(args[i].equals("-rfe"))
				resultsFilterEvidence = true;				
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
					Algorithm.printList("");
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
			else if(args[i].equals("-infoTime")) 
				infoIntervalTime = Double.parseDouble(args[++i]);
			else if(args[i].equals("-od"))
				outputDistFile = args[++i];	
			else if(args[i].equals("-cd"))
				referenceDistFile = args[++i];
			else if(args[i].startsWith("-p") || args[i].startsWith("--")) { // algorithm-specific parameter
				String[] pair = args[i].substring(2).split("=");
				if(pair.length != 2)
					throw new Exception("Argument '" + args[i] + "' for algorithm-specific parameterization is incorrectly formatted.");
				params.put(pair[0], pair[1]);
			}
			else
				throw new Exception("Unknown option " + args[i]);
		}				
	}
	
	public void run() throws Exception {
		if(networkFile == null)
			throw new IllegalArgumentException("No fragment network given");
		if(dbFile == null)
			throw new IllegalArgumentException("No evidence given");
		if(declsFile == null)
			throw new IllegalArgumentException("No model declarations given");
		if(logicFile == null)
			throw new IllegalArgumentException("No logical constraints definitions given");
		if(query == null)
			throw new IllegalArgumentException("No queries given");			
		
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
		db.getParameterHandler().handle(params, false);
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
		sampler.setQueries(queries);
		// - set options
		sampler.setDebugMode(debug);
		if(sampler instanceof BNSampler) {
			((BNSampler)sampler).setMaxTrials(maxTrials);
			((BNSampler)sampler).setSkipFailedSteps(skipFailedSteps);
		}
		sampler.setNumSamples(maxSteps);
		sampler.setInfoInterval(infoInterval);
		sampler.getParameterHandler().handle(params, false);
		// - run inference
		SampledDistribution dist;		
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
			results = tli.getResults(dist);
		}
		else {
			dist = sampler.infer();
			results = sampler.getResults(dist);
		}
		this.samplingTime = sampler.getSamplingTime();
		this.stepsTaken = dist.steps;
		sw.stop();
		
		// print results
		for(InferenceResult res : results) {
			boolean show = true;
			if(resultsFilterEvidence)
				if(db.contains(res.varName))
					show = false;
			if(show) res.print();
		}
		
		// report any unhandled parameters
		Collection<String> unhandledParams = sampler.getParameterHandler().getUnhandledParams();
		if(!unhandledParams.isEmpty())
			System.err.println("Warning: Some parameters could not be handled: " + unhandledParams.toString() + "; supported parameters: " + sampler.getParameterHandler().getHandledParameters().toString());
		
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
	
	/**
	 * @return the results returned by the inference algorithm
	 */
	public Collection<InferenceResult> getResults() {
		return this.results;
	}
	
	/**
	 * @return the number of seconds that the inference algorithm ran for
	 */
	public double getSamplingTime() {
		return samplingTime;
	}
	
	/**
	 * @return the number of steps taken by the inference algorithm that was run
	 */
	public int getNumSteps() {
		return stepsTaken;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			BLNinfer infer = new BLNinfer();
			infer.readArgs(args);	
			infer.run();
		}
		catch(IllegalArgumentException e) {
			System.err.println(e);
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
			Algorithm.printList("                        ");
			System.out.println(
								 "     --<key>=<value>  set algorithm-specific parameter\n" +
						         "     -debug           debug mode with additional outputs\n" + 
						         "     -s               show ground network in editor\n" +
						         "     -si              save ground network instance in BIF format (.instance.xml)\n" +
						         "     -rfe             filter evidence in results\n" +
						         "     -nodetcpt        remove deterministic CPT columns by replacing 0s with low prob. values\n" +
						         "     -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n" +
						         "     -od <file>       save output distribution to file\n" +
						         "     -cd <file>       compare results of inference to reference distribution in file\n" + 
						         "     -py              use Python-based logic engine [deprecated]\n");
			System.exit(1);
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
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
		BasicSampledDistribution.DistributionComparison dc = new DistributionComparison(d1, d2);
		dc.addEntryComparison(new BasicSampledDistribution.ErrorList(d1));
		dc.addEntryComparison(new BasicSampledDistribution.MeanSquaredError(d1));
		dc.addEntryComparison(new BasicSampledDistribution.HellingerDistance(d1));
		dc.compare();		
		dc.printResults();
	}
}
