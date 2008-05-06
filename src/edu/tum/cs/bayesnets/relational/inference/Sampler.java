package edu.tum.cs.bayesnets.relational.inference;

import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SampledDistribution;

public abstract class Sampler extends edu.tum.cs.bayesnets.inference.Sampler {
	public Sampler(BeliefNetworkEx bn) {
		super(bn);
	}
	
	public void printResults(String[] queries) {
		Pattern[] patterns = new Pattern[queries.length];
		for(int i = 0; i < queries.length; i++) {
			String p = queries[i];
			p = Pattern.compile("([,\\(])([a-z][^,\\)]*)").matcher(p).replaceAll("$1.*?");
			p = p.replace("(", "\\(").replace(")", "\\)") + ".*";			
			patterns[i] = Pattern.compile(p);
			//System.out.println("pattern: " + p);
		}
		BeliefNode[] nodes = bn.bn.getNodes();		
		for(int i = 0; i < nodes.length; i++)
			for(int j = 0; j < patterns.length; j++)				
				if(patterns[j].matcher(nodes[i].getName()).matches()) {
					dist.printNodeDistribution(System.out, i);
					break;
				}
	}
	
	public abstract SampledDistribution infer(String[] queries, int numSamples, int infoInterval) throws Exception;
}
