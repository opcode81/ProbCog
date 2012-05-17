/*
 * Created on Apr 27, 2011
 */
package probcog.bayesnets.inference;


public interface IDistributionBuilder {
	public void addSample(WeightedSample s) throws Exception;
	public SampledDistribution getDistribution() throws Exception;
}
