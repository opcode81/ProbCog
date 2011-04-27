/*
 * Created on Apr 27, 2011
 */
package edu.tum.cs.bayesnets.inference;


public interface IDistributionBuilder {
	public void addSample(WeightedSample s);
	public SampledDistribution getDistribution();
}
