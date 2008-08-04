package edu.tum.cs.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.tools.Stopwatch;

public class LikelihoodWeighting extends Sampler {
	int[] nodeOrder;
	
	public LikelihoodWeighting(BeliefNetworkEx bn) {
		super(bn);
		nodeOrder = bn.getTopologicalOrder();
	}
	
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {
		// sample
		Stopwatch sw = new Stopwatch();
		createDistribution();
		System.out.println("sampling...");
		sw.start();
		WeightedSample s = new WeightedSample(bn);
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				System.out.println("  step " + i);			
			WeightedSample ret = getWeightedSample(s, nodeOrder, evidenceDomainIndices); 
			if(ret != null) 
				addSample(ret);
		}
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/sample, %d samples)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep(), dist.steps));
		return dist;
	}
	
	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
		BeliefNode[] nodes = bn.bn.getNodes();
		s.trials = 0;
		boolean successful = false;
loop:	while(!successful) {
			s.weight = 1.0;
			s.trials++;
			if(s.trials > this.maxTrials) {
				if(!this.skipFailedSteps)
					throw new Exception("Could not obtain a countable sample in the maximum allowed number of trials (" + maxTrials + ")");
				else
					return null;
			}
			// assign values to the nodes in order
			for(int i=0; i < nodeOrder.length; i++) {
				int nodeIdx = nodeOrder[i];
				int domainIdx = evidenceDomainIndices[nodeIdx];
				// for evidence nodes, adjust the weight
				if(domainIdx >= 0) { 
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
					if(prob == 0.0) {
						if(debug)
							System.out.println("!!! evidence probability was 0 at node " + nodes[nodeIdx] + " in step " + (dist.steps+1));
						continue loop;
					}
					s.weight *= prob;
				} 
				// for non-evidence nodes, do forward sampling
				else {
					domainIdx = sampleForward(nodes[nodeIdx], s.nodeDomainIndices);
					if(domainIdx < 0) {
						if(debug)
							System.out.println("!!! could not sample forward because of column with only 0s in CPT of " + nodes[nodeIdx].getName() + " in step " + (dist.steps+1));
						bn.removeAllEvidences();
						continue loop;
					}
					s.nodeDomainIndices[nodeIdx] = domainIdx;
				}
			}
			successful = true;
		}
		return s;		
	}
}
