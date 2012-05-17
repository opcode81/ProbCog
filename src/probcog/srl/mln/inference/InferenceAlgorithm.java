/*
 * Created on Aug 5, 2009
 *
 */
package probcog.srl.mln.inference;

import java.util.ArrayList;
import java.util.Vector;
import java.util.regex.Pattern;

import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.logic.GroundAtom;
import probcog.srl.mln.MarkovRandomField;


/**
 * 
 * @author jain
 *
 */
public abstract class InferenceAlgorithm implements IParameterHandler {
	
	protected MarkovRandomField mrf;
	protected ParameterHandler paramHandler;
	protected boolean debug = false;	
	protected boolean verbose = true;
	protected int maxSteps = 5000;
	
	public InferenceAlgorithm(MarkovRandomField mrf) throws Exception {
		this.mrf = mrf;
		paramHandler = new ParameterHandler(this);
		paramHandler.add("debug", "setDebugMode");
		paramHandler.add("verbose", "setVerbose");
		paramHandler.add("maxSteps", "setMaxSteps");
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setMaxSteps(int maxSteps) {
		this.maxSteps = maxSteps;
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
	
	public abstract ArrayList<InferenceResult> infer(Iterable<String> queries) throws Exception;
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}

	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
}
