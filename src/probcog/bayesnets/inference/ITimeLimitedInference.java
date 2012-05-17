/*
 * Created on Nov 13, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.bayesnets.inference;

public interface ITimeLimitedInference {
	public void initialize() throws Exception;
	public SampledDistribution infer() throws Exception;
	public SampledDistribution pollResults() throws Exception;
}
