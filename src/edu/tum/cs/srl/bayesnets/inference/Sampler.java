package edu.tum.cs.srl.bayesnets.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.util.Stopwatch;

/**
 * 
 * @author jain
 */
public abstract class Sampler implements IParameterHandler {
	protected boolean debug = false;
	protected boolean verbose = true;
	protected int numSamples = 1000;
	protected int infoInterval = 100;
	protected ParameterHandler paramHandler;
	protected Vector<Integer> queryVars;
	protected Vector<Integer> queryVarQueryIndices;
	protected AbstractGroundBLN gbln;
	double inferenceTime, initTime;
	protected boolean initialized = false;
	
	public Sampler(AbstractGroundBLN gbln) throws Exception {
		this.gbln = gbln;
		paramHandler = new ParameterHandler(this);
		paramHandler.add("maxSteps", "setNumSamples");
		paramHandler.add("numSamples", "setNumSamples");
		paramHandler.add("infoInterval", "setInfoInterval");
		paramHandler.add("debug", "setDebugMode");
		paramHandler.add("verbose", "setVerbose");
	}
		
	/**
	 * return inference results for the queries that were previously specified
	 * @param dist
	 * @return
	 */
	public Vector<InferenceResult> getResults(SampledDistribution dist) {		
		Vector<InferenceResult> results = new Vector<InferenceResult>();
		int j = 0;
		for(Integer i : queryVars) {
			InferenceResult result = new InferenceResult(dist, i);
			result.queryNo = queryVarQueryIndices.get(j); 
			results.add(result);
			++j;
		}
		return results;
	}
	
	public void printResults(SampledDistribution dist) {
		ArrayList<InferenceResult> results = new ArrayList<InferenceResult>(getResults(dist));		
		Collections.sort(results);
		for(InferenceResult res : results)
			res.print();
	}
	
	public double getTotalInferenceTime() {
		return getInitTime() + getInferenceTime();
	}
	
	public double getInferenceTime() {
		return inferenceTime;
	}
	
	public double getInitTime() {
		return initTime;
	}
	
	public void setNumSamples(int n) {
		numSamples = n;
	}
	
	public void setInfoInterval(int n) {
		infoInterval = n;
	}
	
	public final void initialize() throws Exception {
		if(verbose) 
			System.out.println("initializing...");
		Stopwatch sw = new Stopwatch();
		sw.start();
		_initialize();
		initTime = sw.getElapsedTimeSecs();
		initialized = true;
	}
	
	protected void _initialize() throws Exception {		
	}
	
	public SampledDistribution infer() throws Exception {
		// initialization
		if(!initialized)
			initialize();
		// actual inference
		Stopwatch sw = new Stopwatch();
		sw.start();
		SampledDistribution ret = _infer();
		inferenceTime = sw.getElapsedTimeSecs();
		return ret;
	}
	
	protected abstract SampledDistribution _infer() throws Exception;
	
	public Vector<InferenceResult> inferQueries() throws Exception {
		return getResults(infer());
	}
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	public void setQueries(Iterable<String> queries) {
		// generate patterns
		Vector<Pattern> patterns = new Vector<Pattern>();
		for(String query : queries) {
			String p = query;
			p = Pattern.compile("([,\\(])([a-z][^,\\)]*)").matcher(p).replaceAll("$1.*?");
			p = p.replace("(", "\\(").replace(")", "\\)") + ".*";			
			patterns.add(Pattern.compile(p));
			//System.out.println("pattern: " + p);
		}
		
		// check all ground variables for matches
		// TODO This should be done more efficiently by explicitly grounding the requested nodes instead of using pattern matchers
		BeliefNode[] nodes = gbln.getGroundNetwork().getNodes();
		queryVars = new Vector<Integer>();
		queryVarQueryIndices = new Vector<Integer>();
		for(int i = 0; i < nodes.length; i++) {
			int idxQuery = 0;
			for(Pattern pattern : patterns)	{		
				if(pattern.matcher(nodes[i].getName()).matches()) {
					queryVars.add(i);
					queryVarQueryIndices.add(idxQuery);
					break;					
				}
				++idxQuery;
			}
		}
	}
}
