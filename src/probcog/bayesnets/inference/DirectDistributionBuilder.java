/*
 * Created on Apr 27, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.bayesnets.inference;

public class DirectDistributionBuilder implements IDistributionBuilder {

	SampledDistribution d;
	
	public DirectDistributionBuilder(SampledDistribution d) {
		this.d = d;
	}
	
	@Override
	public void addSample(WeightedSample s) {
		d.addSample(s);
	}

	@Override
	public SampledDistribution getDistribution() {
		return d;
	}
}
