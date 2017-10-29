/*******************************************************************************
 * Copyright (C) 2008-2012 Bernhard Kirchlechner, Dominik Jain.
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

import probcog.clustering.ClusterNamer;
import weka.clusterers.Clusterer;
import weka.core.Instance;

/**
 * An instance of the class <code>ClustererDiscretizationFilter</code> represents
 * a discretization filter based on a clusterer.
 */
public class ClustererDiscretizationFilter implements DiscretizationFilter {

	/**
	 * The output values of the final domain.
	 */
	protected String[] outputValues;
	/**
	 * The clusterer to use to generate the domain splits.
	 */
	protected Clusterer clusterer;

	/**
	 * Constructs an instance for the specified clusterer and cluster namer.
	 * @param <C>
	 * @param clusterer
	 * @param namer
	 * @throws Exception 
	 */
	public <C extends Clusterer> ClustererDiscretizationFilter(C clusterer, ClusterNamer<? super C> namer) throws Exception {
		this.clusterer = clusterer;
		outputValues = namer.getNames(clusterer);
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
			int cluster = clusterer.clusterInstance(inst);
			return outputValues[cluster];
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#addOutputValue(java.lang.String)
	 */
	public void addOutputValue(String outputValue) {
		// FIXME: What should we do here? We cannot recreate the Clusterer from the output values, can we?
		throw new IllegalArgumentException("Cannot add outputValue for clusterer domains!");
	}

	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getExampleValue(int)
	 */
	public double getExampleValue(int bin) {
		throw new IllegalArgumentException("Cannot sample cluster values!");
	}
	
	/* (non-Javadoc)
	 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getIntervals(int)
	 */
	public double[] getIntervals(int bin) {
		throw new IllegalArgumentException("Cannot get cluster intervals!");
	}
}
