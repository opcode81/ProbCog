/*
 * Created on Oct 26, 2007
 */
package edu.tum.cs.bayesnets.core;

import edu.tum.cs.clustering.ClusterNamer;
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
	 */
	public <C extends Clusterer> ClustererDiscretizationFilter(C clusterer, ClusterNamer<? super C> namer) {
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
