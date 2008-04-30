package edu.tum.cs.bayesnets.inference;

import java.util.Random;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.SampledDistribution;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.WeightedSample;

public abstract class Sampler {
	public BeliefNetworkEx bn;
	public SampledDistribution dist;
	
	public Sampler() {	
	}
	
	protected void createDistribution(BeliefNetworkEx bn) {
		this.bn = bn;
		this.dist = new BeliefNetworkEx.SampledDistribution(bn);
	}
	
	protected void addSample(WeightedSample s) {
		this.dist.addSample(s);
	}
	
	public static int sample(double[] distribution, Random generator) {
		double sum = 0;
		for(int i = 0; i < distribution.length; i++)
			sum += distribution[i];
		return sample(distribution, sum, generator);
	}
	
	public static int sample(double[] distribution, double sum, Random generator) {
		double random = generator.nextDouble() * sum;		
		int ret = 0;
		sum = 0;
		int i = 0;
		while(sum < random && i < distribution.length) {			
			sum += distribution[ret = i++];
		}
		return sum >= random ? ret : -1;		
	}
}
