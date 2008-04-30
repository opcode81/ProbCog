package edu.tum.cs.bayesnets.inference;

import java.util.Random;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.SampledDistribution;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.WeightedSample;
import edu.tum.cs.tools.Stopwatch;

public class LikelihoodWeighting extends Sampler {
	int[] nodeOrder;
	
	public LikelihoodWeighting(BeliefNetworkEx bn) {
		this.bn = bn;
		nodeOrder = bn.getTopologicalOrder();
	}
	
	public SampledDistribution infer(int[] evidenceDomainIndices, int numSamples, int infoInterval) throws Exception {
		// sample
		Stopwatch sw = new Stopwatch();
		createDistribution(bn);
		Random generator = new Random();
		System.out.println("sampling...");
		sw.start();
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				System.out.println("  step " + i);
			WeightedSample s = bn.getWeightedSample(nodeOrder, evidenceDomainIndices, generator); 
			addSample(s);
		}
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/step)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep()));
		return dist;
	}
}
