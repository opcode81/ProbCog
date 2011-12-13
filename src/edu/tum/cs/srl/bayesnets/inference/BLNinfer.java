package edu.tum.cs.srl.bayesnets.inference;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.CPT;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BNDatabase;
import edu.tum.cs.bayesnets.inference.ITimeLimitedInference;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.inference.BasicSampledDistribution;
import edu.tum.cs.inference.GeneralSampledDistribution;
import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.bln.AbstractBayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.py.BayesianLogicNetworkPy;
import edu.tum.cs.util.Stopwatch;

/**
 * BLN inference tool
 * @author jain
 */
public class BLNinfer implements IParameterHandler {	
	String declsFile = null;
	String networkFile = null;
	String logicFile = null; 
	String dbFile = null;
	boolean useMaxSteps = false;
	Algorithm algo = Algorithm.LikelihoodWeighting;
	String[] cwPreds = null;
	boolean showBN = false;
	boolean usePython = false;
	boolean verbose = true;
	boolean saveInstance = false;
	boolean noInference = false;
	boolean skipFailedSteps = false;
	boolean removeDeterministicCPTEntries = false;
	boolean resultsFilterEvidence = false;
	double timeLimit = 10.0, infoIntervalTime = 1.0;
	boolean timeLimitedInference = false;
	boolean samplerInitializationBeforeTimingStarts = true;
	boolean allowPartialInst = false;
	String outputDistFile = null, referenceDistFile = null;
	Map<String,Object> params;
	AbstractBayesianLogicNetwork bln = null;
	AbstractGroundBLN gbln = null;
	Database db = null;
	Iterable<String> queries = null;
	ParameterHandler paramHandler;	
	Sampler sampler;
	TimeLimitedInference tli;
	
	enum SortOrder implements Comparator<InferenceResult> { 
		Atom {
			public int compare(InferenceResult o1, InferenceResult o2) {
				return o1.varName.compareTo(o2.varName);
			}
		},
		Probability {
			public int compare(InferenceResult o1, InferenceResult o2) {
				return -Double.compare(o1.probabilities[0], o2.probabilities[0]);
			}
		},
		PredicateProbability  {
			public int compare(InferenceResult o1, InferenceResult o2) {
				String pred1 = o1.varName.substring(0, o1.varName.indexOf('('));
				String pred2 = o2.varName.substring(0, o2.varName.indexOf('('));
				int res = pred1.compareTo(pred2);
				if(res != 0)
					return res;
				else
					return -Double.compare(o1.probabilities[0], o2.probabilities[0]);
			}
		};
	};	
	SortOrder resultsSortOrder = SortOrder.Atom;
	
	// computed stuff
	Collection<InferenceResult> results;
	double groundingTime, inferenceInitTime, inferenceTime;
	int stepsTaken;
	
	public BLNinfer() throws Exception {
		this(new HashMap<String,Object>());
	}
	
	public BLNinfer(Map<String,Object> params) throws Exception {
		paramHandler = new ParameterHandler(this);
		paramHandler.add("verbose", "setVerbose");		
		paramHandler.add("maxSteps", "setMaxSteps");
		paramHandler.add("numSamples", "setMaxSteps");
		paramHandler.add("inferenceMethod", "setInferenceMethod");
		paramHandler.add("timeLimit", "setTimeLimit");
		this.params = params;
	}
	
	public void setVerbose(Boolean verbose) {		
		this.verbose = verbose;
	}
	
	public void setMaxSteps(Integer steps) {		
		useMaxSteps = true;
	}
	
	public void setInferenceMethod(String methodName) {
		try {
			algo = Algorithm.valueOf(methodName);
		}
		catch(IllegalArgumentException e) {
			System.err.println("Error: Unknown inference algorithm '" + methodName + "'");	
			Algorithm.printList("");
			System.exit(1);
		}
	}
	
	public void setTimeLimit(double seconds) {
		timeLimitedInference = true;
		this.timeLimit = seconds;
	}

	public void readArgs(String[] args) throws Exception {
		// read arguments
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-b"))
				declsFile = args[++i];
			else if(args[i].equals("-x"))
				networkFile = args[++i];
			else if(args[i].equals("-l"))
				logicFile = args[++i];
			else if(args[i].equals("-q")) {
				String query = args[++i];
				Pattern comma = Pattern.compile("\\s*,\\s*");
				String[] candQueries = comma.split(query);
				Vector<String> queries = new Vector<String>();
				String q = "";
				for(int j = 0; j < candQueries.length; j++) {
					if(!q.equals(""))
						q += ",";
					q += candQueries[j];
					if(balancedParentheses(q)) {
						queries.add(q);
						q = "";
					}
				}
				this.queries = queries;
				if(!q.equals(""))
					throw new IllegalArgumentException("Unbalanced parentheses in queries");
			}
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
			else if(args[i].equals("-ni"))
				noInference = true;				
			else if(args[i].equals("-skipFailedSteps"))
				skipFailedSteps = true;				
			else if(args[i].equals("-py"))
				usePython = true;				
			else if(args[i].equals("-cw"))
				cwPreds = args[++i].split(",");		
			else if(args[i].equals("-maxSteps")) {
				int steps = Integer.parseInt(args[++i]);
				params.put("numSamples", steps); 
				setMaxSteps(steps);
			}
			else if(args[i].equals("-allowPartialInst"))
				allowPartialInst = true;
			else if(args[i].equals("-maxTrials"))
				params.put("maxTrials", args[++i]);
			else if(args[i].equals("-ia"))
				setInferenceMethod(args[++i]);
			else if(args[i].equals("-infoInterval"))
				params.put("infoInterval", args[++i]);
			else if(args[i].equals("-debug"))
				params.put("debug", Boolean.TRUE);
			else if(args[i].equals("-t")) {
				if(i+1 < args.length && !args[i+1].startsWith("-"))
					setTimeLimit(Double.parseDouble(args[++i]));
				else
					setTimeLimit(timeLimit);
			}
			else if(args[i].equals("-infoTime")) 
				infoIntervalTime = Double.parseDouble(args[++i]);
			else if(args[i].equals("-od"))
				outputDistFile = args[++i];	
			else if(args[i].equals("-cd"))
				referenceDistFile = args[++i];
			else if(args[i].startsWith("-O")) {
				String order = args[i].substring(2);
				if(order.equals("a"))
					resultsSortOrder = SortOrder.Atom;
				else if(order.equals("p"))
					resultsSortOrder = SortOrder.Probability;
				else if(order.equals("pp"))
					resultsSortOrder = SortOrder.PredicateProbability;
				else
					throw new Exception("Unknown sort order '" + order + "'");
			}
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
	
	public void setBLN(AbstractBayesianLogicNetwork bln) {
		this.bln = bln;
	}
	
	public void setDatabase(Database db) {
		this.db = db;
	}
	
	public void setQueries(Iterable<String> queries) {
		this.queries = queries;
	}
	
	public void setGroundBLN(AbstractGroundBLN gbln) {
		this.gbln = gbln;
		setBLN(gbln.getBLN());
		setDatabase(gbln.getDatabase());		
	}
	
	public Collection<InferenceResult> run() throws Exception {
		if(bln == null) { 
			if(networkFile == null)
				throw new IllegalArgumentException("No fragment network given");
			if(declsFile == null)
				throw new IllegalArgumentException("No model declarations given");
			//if(logicFile == null)
			//	throw new IllegalArgumentException("No logical constraints definitions given");
		}
		if(dbFile == null && db == null)
			throw new IllegalArgumentException("No evidence given");
		if(queries == null)
			throw new IllegalArgumentException("No queries given");			

		// handle parameters
		paramHandler.handle(params, false);		
		
		// load relational model		
		if(bln == null) {
			if(!usePython) 
				bln = new BayesianLogicNetwork(declsFile, networkFile, logicFile);
			else
				bln = new BayesianLogicNetworkPy(declsFile, networkFile, logicFile);
		}
		RelationalBeliefNetwork blog = bln;
		
		// (on request) remove deterministic dependencies in CPTs
		if(removeDeterministicCPTEntries) {
			final double lowProb = 0.001; 
			for(BeliefNode node : blog.bn.getNodes()) {
				CPT cpf = (CPT)node.getCPF();					
				for(int i = 0; i < cpf.size(); i++)
					if(cpf.getDouble(i) == 0.0)
						cpf.put(i, new ValueDouble(lowProb));
				cpf.normalizeByDomain();
			}
		}
		
		// read evidence database
		if(db == null)
			db = new Database(blog);
		paramHandler.addSubhandler(db.getParameterHandler());
		if(dbFile != null)
			db.readBLOGDB(dbFile);
		if(cwPreds != null) {
			for(String predName : cwPreds)
				db.setClosedWorldPred(predName);
		}
		
		// instantiate ground model
		if(gbln == null) {
			Stopwatch sw = new Stopwatch();
			sw.start();
			bln.setAllowPartialInstantiation(allowPartialInst);
			gbln = bln.ground(db);
			paramHandler.addSubhandler(gbln);
			gbln.instantiateGroundNetwork();
			this.groundingTime = sw.getElapsedTimeSecs();
		}
		if(showBN) {
			gbln.getGroundNetwork().show();
		}
		if(saveInstance) {
			// save Bayesian network
			String baseName = networkFile.substring(0, networkFile.lastIndexOf('.'));
			gbln.getGroundNetwork().saveXMLBIF(baseName + ".instance.xml");
			// save evidence data
			BNDatabase bndb = new BNDatabase();
			for(edu.tum.cs.srl.Variable var : db.getEntries())
				bndb.add(var.getName(), var.value);
			bndb.write(new PrintStream(new File(baseName + ".instance.bndb")));
		}
		
		if(noInference)
			return null;
		
		// read reference distribution if any
		GeneralSampledDistribution referenceDist = null;
		int[] evidenceDomainIndices = null; // to filter out evidence in distribution comparisons
		if(referenceDistFile != null) {
			referenceDist = GeneralSampledDistribution.fromFile(new File(referenceDistFile));
			evidenceDomainIndices = gbln.getFullEvidence(gbln.getDatabase().getEntriesAsArray());
		}
		
		// run inference
		Stopwatch sw = new Stopwatch();
		sw.start();
		// - create sampler and pass on parameters
		sampler = algo.createSampler(gbln);
		sampler.setQueries(queries);
		// - set options
		paramHandler.addSubhandler(sampler);
		// - run inference
		SampledDistribution dist;
		if(timeLimitedInference) {
			if(!(sampler instanceof ITimeLimitedInference)) 
				throw new Exception(sampler.getAlgorithmName() + " does not support time-limited inference");					
			ITimeLimitedInference tliSampler = (ITimeLimitedInference) sampler;
			if(!useMaxSteps)				
				sampler.setNumSamples(Integer.MAX_VALUE);
			sampler.setInfoInterval(Integer.MAX_VALUE); // provide intermediate results only triggered by time-limited inference
			tli = new TimeLimitedInference(tliSampler, timeLimit, infoIntervalTime);			
			paramHandler.addSubhandler(tli);
			tli.setReferenceDistribution(referenceDist);
			tli.setEvidenceDomainIndices(evidenceDomainIndices);
			if(samplerInitializationBeforeTimingStarts)
				tliSampler.initialize(); // otherwise initialization is called by infer()
			dist = tli.run();
			if(referenceDist != null)
				System.out.println("MSEs: " + tli.getMSEs());
			results = tli.getResults(dist);
		}
		else {
			dist = sampler.infer();
			results = sampler.getResults(dist);
		}
		this.inferenceTime = sampler.getInferenceTime();
		this.inferenceInitTime = sampler.getInitTime();
		if(dist != null)
			this.stepsTaken = dist.steps;
		sw.stop();
		
		// print results
		if(verbose) {
			ArrayList<InferenceResult> sortedResults = new ArrayList<InferenceResult>(results);
			Collections.sort(sortedResults, this.resultsSortOrder);
			for(InferenceResult res : sortedResults) {
				boolean show = true;
				if(resultsFilterEvidence)
					if(db.contains(res.varName))
						show = false;
				if(show) res.print();
			}
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
		if(referenceDist != null && dist != null) {				
			System.out.println("comparing to reference distribution...");
			BasicSampledDistribution.compareDistributions(dist, referenceDist, evidenceDomainIndices);
		}
		
		return results;
	}
	
	/**
	 * @return the results returned by the inference algorithm
	 */
	public Collection<InferenceResult> getResults() {
		return this.results;
	}
	
	/**
	 * @return the total number of seconds that the inference algorithm ran for (init + computation)
	 */
	public double getTotalInferenceTime() {
		return getInferenceTime() + getInferenceInitTime();
	}
	
	/**
	 * @return the number of seconds the actual inference method ran (without initialization)
	 */
	public double getInferenceTime() {
		return inferenceTime;
	}
	
	/**
	 * @return number of seconds taken to instantiate the ground model
	 */
	public double getGroundingTime() {
		return groundingTime;
	}
	
	public double getInferenceInitTime() {
		return inferenceInitTime;
	}
	
	/**
	 * @return the number of steps taken by the inference algorithm that was run
	 */
	public int getNumSteps() {
		return stepsTaken;
	}
	
	public Sampler getInferenceObject() {
		return sampler;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Locale.setDefault(new Locale("en"));
		try {
			BLNinfer infer = new BLNinfer();
			infer.readArgs(args);
			infer.run();
			// report any unhandled parameters
			ParameterHandler handler = infer.getParameterHandler();
			Collection<String> unhandledParams = handler.getUnhandledParams();
			if(!unhandledParams.isEmpty()) {
				System.err.println("Warning: Some parameters could not be handled: " + unhandledParams.toString() + "; supported parameters: ");
				//handler.getHandledParameters().toString()
				handler.printHelp(System.err);
			}
		}
		catch(IllegalArgumentException e) {
			e.printStackTrace();
			//System.err.println(e);
			System.out.println("\n usage: BLNinfer <arguments>\n\n" +
					             "   required arguments:\n\n" +
					             "     -b <declarations file>    declarations file (types, domains, signatures, etc.)\n" +
					             "     -x <network file>         fragment network (XML-BIF or PMML)\n" + 
					             "     -l <logic file>           logical constraints file\n" + 
					             "     -e <evidence db pattern>  an evidence database file or file mask\n" +
					             "     -q <comma-sep. queries>   queries (predicate names or partially grounded terms with lower-case vars)\n\n" +
					             "   options:\n\n" +
					             "     -allowPartialInst  allow partial ground network instantiations (skip nodes with no applicable fragment)\n" + 
								 "     -maxSteps #        the maximum number of steps to take (default: 1000 for non-time-limited inf.)\n" +
								 "     -maxTrials #       the maximum number of trials per step for BN sampling algorithms (default: 5000)\n" +
								 "     -infoInterval #    the number of steps after which to output a status message\n" +
								 "     -skipFailedSteps   failed steps (> max trials) should just be skipped\n\n" +	
								 "     -t [secs]          use time-limited inference (default: 10 seconds)\n" +
								 "     -infoTime #        interval in secs after which to display intermediate results (time-limited inference, default: 1.0)\n" +
								 "     -ia <name>         inference algorithm selection; valid names:");
			Algorithm.printList("                        ");
			System.out.println(
								 "     --<key>=<value>    set algorithm-specific parameter\n" +
						         "     -debug             debug mode with additional outputs\n" + 
						         "     -s                 show ground network in editor\n" +
						         "     -si                save ground network instance in BIF format (.instance.xml) and evidence (.instance.bndb)\n" +
						         "     -ni                do not actually run the inference method (only instantiate ground network)" +
						         "     -rfe               filter evidence in results\n" +
						         "     -nodetcpt          remove deterministic CPT columns by replacing 0s with low prob. values\n" +
						         "     -cw <predNames>    set predicates as closed-world (comma-separated list of names)\n" +
						         "     -O<a|p|pp>         order printed results by atom name (a), probability (p), predicate then probability (pp)\n" +
						         "     -od <file>         save output distribution to file\n" +
						         "     -cd <file>         compare results of inference to reference distribution in file\n" + 
						         "     -py                use Python-based logic engine [deprecated]\n");
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
	
	@Override
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	/**
	 * If time-limited inference was performed, returns the corresponding object
	 * @return  an instance of {@link TimeLimitedInference} 
	 * 			(or null if time-limited inference was not carried out)
	 */
	public TimeLimitedInference getTimeLimitedInference() {
		return this.tli;
	}
}
