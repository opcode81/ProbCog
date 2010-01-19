package edu.tum.cs.srl.bayesnets.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;

public abstract class Sampler implements IParameterHandler {
	protected boolean debug = false;
	protected int numSamples = 1000;
	protected int infoInterval = 100;
	protected ParameterHandler paramHandler;
	
	public Sampler() throws Exception {
		paramHandler = new ParameterHandler(this);
		paramHandler.add("maxSteps", "setNumSamples");
		paramHandler.add("infoInterval", "setInfoInterval");
		paramHandler.add("debug", "setDebugMode");
	}
	
	public static Vector<InferenceResult> getResults(SampledDistribution dist, Iterable<String> queries) {
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
		Vector<InferenceResult> results = new Vector<InferenceResult>();
		BeliefNode[] nodes = dist.bn.bn.getNodes();		
		for(int i = 0; i < nodes.length; i++)
			for(Pattern pattern : patterns)				
				if(pattern.matcher(nodes[i].getName()).matches()) {
					results.add(new InferenceResult(dist, i));
					break;
				}
		return results;
	}
	
	public static void printResults(SampledDistribution dist, Iterable<String> queries) {
		ArrayList<InferenceResult> results = new ArrayList<InferenceResult>(getResults(dist, queries));		
		Collections.sort(results);
		for(InferenceResult res : results)
			res.print();
	}
	
	public void setNumSamples(int n) {
		numSamples = n;
	}
	
	public void setInfoInterval(int n) {
		infoInterval = n;
	}
	
	public abstract SampledDistribution infer() throws Exception;
	
	public Vector<InferenceResult> infer(Iterable<String> queries) throws Exception {
		return getResults(infer(), queries);
	}
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public void handleParams(java.util.Map<String,String> params) throws Exception {
		paramHandler.handle(params);
	}
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
}
