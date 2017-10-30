/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.directed.inference;

import java.util.Vector;

import probcog.bayesnets.inference.ITimeLimitedInference;
import probcog.bayesnets.inference.SampledDistribution;
import probcog.exception.ProbCogException;

/**
 * Time-limited inference wrapper for Bayesian logic networks.
 * @author Dominik Jain
 */
public class TimeLimitedInference extends probcog.bayesnets.inference.TimeLimitedInference {

	Sampler inference;
	
	public TimeLimitedInference(ITimeLimitedInference inference, double time, double interval) throws ProbCogException {
		super(inference, time, interval);
		this.inference = (Sampler)inference;
	}
	
	@Override
	protected void printResults(SampledDistribution dist) {	
		inference.printResults(dist);
	}
	
	public Vector<InferenceResult> getResults(SampledDistribution dist) {
		return inference.getResults(dist);
	}
}
