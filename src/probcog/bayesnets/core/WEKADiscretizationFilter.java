/*******************************************************************************
 * Copyright (C) 2008-2012 Bernhard Kirchlechner.
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
package probcog.bayesnets.core;

import probcog.bayesnets.learning.FilterNamer;
import weka.core.Instance;
import weka.filters.Filter;
import weka.filters.UnsupervisedFilter;

/**
 * An instance of the class <code>WEKADiscretizationFilter</code> represents
 * a discretization filter based on a clusterer.
 */
public class WEKADiscretizationFilter implements DiscretizationFilter {

	/**
	 * The output values.
	 */
	protected String[] outputValues;
	/**
	 * The WEKA filter to use for discretization.
	 */
	protected Filter filter;

	/**
	 * Construct an instance of <code>WEKADiscretizationFilter</code> given a filter and a namer for the filter.
	 * @param <F> the filter type.
	 * @param filter the filter.
	 * @param namer the filter namer corresponding to the given filter.
	 */
	public <F extends Filter> WEKADiscretizationFilter(F filter, FilterNamer<? super F> namer) {
		if (filter instanceof UnsupervisedFilter) {
			throw new IllegalArgumentException("Cannot use unsupervised filter for descretization (the instances are handled one by one)!");
		}
		this.filter = filter;
		outputValues = namer.getNames(filter);
	}

	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getOutputValues()
	 */
	public String[] getOutputValues() {
		return outputValues;
	}

	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getValueForContinuous(double)
	 */
	public String getValueForContinuous(double continuous) {
		Instance inst = new Instance(1);
		inst.setValue(0, continuous);
		try {
			filter.input(inst);
			filter.batchFinished();
			Instance newInst=filter.output();
			int value = (int)newInst.value(0);
			return outputValues[value];
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#addOutputValue(java.lang.String)
	 */
	public void addOutputValue(String outputValue) {
		throw new IllegalArgumentException("Cannot recreate filter from output values, yet!");
	}

	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getExampleValue(int)
	 */
	public double getExampleValue(int bin) {
		throw new IllegalArgumentException("Cannot sample from WEKADiscretization filter!");
	}

	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getIntervals(int)
	 */
	public double[] getIntervals(int bin) {
		throw new IllegalArgumentException("Cannot sample from WEKADiscretization filter!");
	}
}
