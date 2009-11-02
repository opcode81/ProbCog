package edu.tum.cs.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.util.Stopwatch;

public class EnumerationAsk extends Sampler {
	int[] nodeOrder;
	int numPruned;
	
	public EnumerationAsk(BeliefNetworkEx bn) {
		super(bn);
		nodeOrder = bn.getTopologicalOrder();
	}
	
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {
		Stopwatch sw = new Stopwatch();
		numPruned = 0;
		createDistribution();
		System.out.println("enumerating worlds...");
		sw.start();
		WeightedSample s = new WeightedSample(bn);
		enumerateWorlds(s, nodeOrder, evidenceDomainIndices, 0); 
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%d worlds enumerated, %d paths pruned)\n", sw.getElapsedTimeSecs(), dist.steps, numPruned));
		return dist;
	}
	
	public void enumerateWorlds(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices, int i) throws Exception {
		//System.out.println("enum " + nodes[nodeOrder[i]].getName());
		// if we have completed the world, we are done and can add the world as a sample
		if(i == nodes.length) {
			//System.out.println("counting sample");
			addSample(s);
			return;
		}
		// otherwise continue
		int nodeIdx = nodeOrder[i];
		int domainIdx = evidenceDomainIndices[nodeIdx];
		// for evidence nodes, adjust the weight
		if(domainIdx >= 0) { 
			s.nodeDomainIndices[nodeIdx] = domainIdx;
			double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
			s.weight *= prob;
			if(prob == 0.0) { // we have reached zero, so we can save us the trouble of further ramifications
				//System.out.println("zero reached");
				numPruned++;
				return;
			}			
			enumerateWorlds(s, nodeOrder, evidenceDomainIndices, i+1);
		} 
		// for non-evidence nodes, consider all settings
		else {
			Domain d = nodes[nodeIdx].getDomain();
			int order = d.getOrder();
			//System.out.println("  enumerating all " + order + " cases for " + nodes[nodeIdx].getName());			
			double weight = s.weight;
			for(int j = 0; j < order; j++) { 
				s.nodeDomainIndices[nodeIdx] = j;
				double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
				if(prob == 0.0) {
					//System.out.println("zero reached");
					numPruned++;
					continue;
				}
				s.weight = weight * prob;
				enumerateWorlds(s, nodeOrder, evidenceDomainIndices, i+1);
			}
		}			
	}
}
