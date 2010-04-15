/*
 * Created on Aug 5, 2009
 *
 */
package edu.tum.cs.srl.mln.inference;

import java.util.ArrayList;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.srl.mln.MarkovRandomField;

/**
 * 
 * @author jain
 *
 */
public abstract class InferenceAlgorithm implements IParameterHandler {
	
	protected MarkovRandomField mrf;
	protected ParameterHandler paramHandler;
	protected boolean debug = false;	
	
	public InferenceAlgorithm(MarkovRandomField mrf) throws Exception {
		this.mrf = mrf;
		paramHandler = new ParameterHandler(this);
		paramHandler.add("debug", "setDebugMode");
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public abstract double getResult(GroundAtom ga);	
	
	public ArrayList<InferenceResult> getResults(Iterable<String> queries) {
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
		ArrayList<InferenceResult> results = new ArrayList<InferenceResult>();
		int numRes = 0;
		for(GroundAtom ga : mrf.getWorldVariables())
			for(Pattern pattern : patterns)				
				if(pattern.matcher(ga.toString()).matches()) {
					results.add(new InferenceResult(ga, getResult(ga)));
					numRes++;
					break;
				}
		if(numRes == 0)
			System.err.println("Warning: None of the queries could be matched to a variable.");
		return results;
	}
	
	public abstract ArrayList<InferenceResult> infer(Iterable<String> queries, int maxSteps) throws Exception;
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}

	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
}
