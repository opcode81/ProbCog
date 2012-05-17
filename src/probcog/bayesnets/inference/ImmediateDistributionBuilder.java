package probcog.bayesnets.inference;

/**
 * Distribution builder that builds up a distribution at once -
 * simply by setting the entire distribution. 
 * This is for non-sampling-based algorithms
 * 
 * @author jain
 */
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
