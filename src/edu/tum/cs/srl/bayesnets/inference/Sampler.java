package edu.tum.cs.srl.bayesnets.inference;

import java.util.Vector;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.inference.SampledDistribution;

public abstract class Sampler {
	protected boolean debug = false;
	protected int numSamples = 1000;
	protected int infoInterval = 100;
	
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
		for(InferenceResult res : getResults(dist, queries))
			res.print();
	}
	
	public void setNumSamples(int n) {
		numSamples = n;
	}
	
	public void setInfoInterval(int n) {
		infoInterval = n;
	}
	
	public abstract Vector<InferenceResult> infer(Iterable<String> queries) throws Exception;
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public SampledDistribution pollResults() throws Exception {
		throw new Exception("Time-limited inference not supported by " + getAlgorithmName());
	}
}
