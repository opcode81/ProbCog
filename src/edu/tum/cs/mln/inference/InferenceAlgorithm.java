/*
 * Created on Aug 5, 2009
 *
 */
package edu.tum.cs.mln.inference;

import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.srl.mln.MarkovRandomField;

/**
 * 
 * @author jain
 *
 */
public abstract class InferenceAlgorithm {
	
	protected MarkovRandomField mrf;
	
	public InferenceAlgorithm(MarkovRandomField mrf) {
		this.mrf = mrf;
	}
	
	public abstract double getResult(GroundAtom ga);
	
	public Vector<InferenceResult> getResults(Iterable<String> queries) {
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
	
	public abstract Vector<InferenceResult> infer(Iterable<String> queries, int maxSteps) throws Exception;
}
