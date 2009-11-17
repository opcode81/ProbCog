/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import edu.tum.cs.bayesnets.inference.ITimeLimitedInference;
import edu.tum.cs.bayesnets.inference.SampledDistribution;

public class TimeLimitedInference extends edu.tum.cs.bayesnets.inference.TimeLimitedInference {

	SampledDistribution dist;
	
	public TimeLimitedInference(ITimeLimitedInference inference, Iterable<String> queries, double time, double interval) {
		super(inference, queries, time, interval);
	}
	
	@Override
	protected void printResults(SampledDistribution dist) {	
		Sampler.printResults(dist, queries);
	}
}
