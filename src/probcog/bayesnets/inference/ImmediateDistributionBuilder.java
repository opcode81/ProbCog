/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain.
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
package probcog.bayesnets.inference;

/**
 * Distribution builder that builds up a distribution at once -
 * simply by setting the entire distribution. 
 * This is for non-sampling-based algorithms
 * 
 * @author Dominik Jain
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
