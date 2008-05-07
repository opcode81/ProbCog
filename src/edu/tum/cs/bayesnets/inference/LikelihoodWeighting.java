package edu.tum.cs.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.DiscreteEvidence;
import edu.ksu.cis.bnj.ver3.inference.approximate.sampling.ForwardSampling;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.tools.Stopwatch;

public class LikelihoodWeighting extends Sampler {
	int[] nodeOrder;
	static final int MAX_TRIALS = 5000;
	
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
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				System.out.println("  step " + i);
			WeightedSample s = getWeightedSample(nodeOrder, evidenceDomainIndices); 
			addSample(s);
		}
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/step)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep()));
		return dist;
	}
	
	public WeightedSample getWeightedSample(int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
		BeliefNode[] nodes = bn.bn.getNodes();
		int[] sampleDomainIndices  = new int[nodes.length];
		boolean successful = false;
		double weight = 1.0;
		int trials=0;
success:while(!successful) {
			//System.out.println(trials);
			weight = 1.0;
			if (trials > MAX_TRIALS)
				return null;
			for (int i=0; i< nodeOrder.length; i++) {
				int nodeIdx = nodeOrder[i];
				int domainIdx = evidenceDomainIndices[nodeIdx];
				if (domainIdx >= 0) { // This is an evidence node?
					sampleDomainIndices[nodeIdx] = domainIdx;
					nodes[nodeIdx].setEvidence(new DiscreteEvidence(domainIdx));
					double prob = getCPTProbability(nodes[nodeIdx], sampleDomainIndices);
					if (prob == 0.0) {
						//System.out.println("sampling failed at evidence node " + nodes[nodeIdx].getName());
						bn.removeAllEvidences();
						trials++;
						continue success;
					}
					weight *= prob;
				} 
				else {
					domainIdx = ForwardSampling.sampleForward(nodes[nodeIdx], bn.bn, generator);
					if (domainIdx < 0) {
						System.out.println("could not sample forward because of column with 0s in CPT of " + nodes[nodeIdx].getName());
						bn.removeAllEvidences();
						trials++;
						continue success;
					}
					sampleDomainIndices[nodeIdx] = domainIdx;
					nodes[nodeIdx].setEvidence(new DiscreteEvidence(domainIdx));
				}
			}
			trials++;
			bn.removeAllEvidences();
			successful = true;
		}
		return new WeightedSample(bn, sampleDomainIndices, weight, null, trials);		
	}
}
