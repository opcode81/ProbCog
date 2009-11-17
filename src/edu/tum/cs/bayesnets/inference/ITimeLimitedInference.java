/*
 * Created on Nov 13, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

public interface ITimeLimitedInference {
	public SampledDistribution infer() throws Exception;
	public SampledDistribution pollResults() throws Exception;
}
