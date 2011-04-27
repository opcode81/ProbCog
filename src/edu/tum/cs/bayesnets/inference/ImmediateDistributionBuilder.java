/*
 * Created on Apr 27, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

public class ImmediateDistributionBuilder implements IDistributionBuilder {

	protected SampledDistribution d;
	
	@Override
	public void addSample(WeightedSample s) {
		throw new UnsupportedOperationException();
	}
	
	public void setDistribution(SampledDistribution d) {
		this.d = d;
	}

	@Override
	public SampledDistribution getDistribution() {
		return d;
	}

}
