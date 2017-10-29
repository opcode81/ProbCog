/*******************************************************************************
 * Copyright (C) 2007-2012 Bernhard Kirchlechner, Dominik Jain.
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
import edu.ksu.cis.bnj.ver3.core.Discrete;

/**
 * An instance of class <code>Discretized</code> represents a domain that
 * is originally continuous but is discretized by some discretization filter.
 * This class has to wrap all methods of Discrete because all members of Discrete are private. :( 
 */
public class Discretized extends Discrete {
	/**
	 * The discretization filter to use.
	 */
	protected DiscretizationFilter discretizationFilter;
	/**
	 * The domain we delegate all methods to.
	 */
	Discrete discrete;

	/**
	 * Construct a <code>Discretized</code> instance with a clusterer for discretization.
	 * @param clusterer	the clusterer used for discretization.
	 * @throws Exception 
	 */
	public <C extends Clusterer> Discretized(C clusterer, ClusterNamer<? super C> namer) throws Exception {
		setDiscretizationFilter(new ClustererDiscretizationFilter(clusterer, namer));
	}

	/**
	 * Construct a <code>Discretized</code> instance with a <code>DiscretizationFilter</code> 
	 * for discretization.
	 * @param filter	the filter used for discretization. 
	 */
	public Discretized(DiscretizationFilter filter) {
		setDiscretizationFilter(filter);
	}
	
	public Discretized() {
		setDiscretizationFilter(new DiscretizationFilter.Default(new double[0]));
	}

	/**
	 * Get the discretization filter.
	 * @return the discretization filter.
	 */
	public DiscretizationFilter getDiscretizationFilter() {
		return discretizationFilter;
	}

	/**
	 * Set the discretization filter.
	 * @param filter	the discretization filter to be used for discretization.
	 */
	public void setDiscretizationFilter(DiscretizationFilter filter) {
		discretizationFilter = filter;
		if (filter != null)
			discrete=new Discrete(discretizationFilter.getOutputValues()); 
	}

	/**
	 * Get the discrete value.
	 * @param value	the value to be discretized.
	 * @return		the discretized value.
	 */
	public String getNameFromContinuous(double value) {
		return discretizationFilter.getValueForContinuous(value);
	}

	/**
	 * Add a name to the domain.
	 * ATTENTION: This method must not be called and throws an exception because the names of this domain are generated! 
	 * @param outcome	the name.
	 * @see edu.ksu.cis.bnj.ver3.core.Discrete#addName(java.lang.String)
	 */
	public void addName(String outcome) {
		discretizationFilter.addOutputValue(outcome);
		setDiscretizationFilter(discretizationFilter);
	}

	/**
	 * Find a name in the discrete and get its value.
	 * @param outcome	the name of the element.
	 * @return		the element number for the name.
	 * @see edu.ksu.cis.bnj.ver3.core.Discrete#findName(java.lang.String)
	 */
	public int findName(String outcome) {
		return discrete.findName(outcome);
	}

	/**
	 * Get the o'th element in the domain.
	 * @param o		the element number.
	 * @return		the name of the element.
	 * @see edu.ksu.cis.bnj.ver3.core.Discrete#getName(int)
	 */
	public String getName(int o) {
		return discrete.getName(o);
	}

	/**
	 * Get the order of this domain.
	 * @return		the order of the domain.
	 * @see edu.ksu.cis.bnj.ver3.core.Discrete#getOrder()
	 */
	public int getOrder() {
		return discrete.getOrder();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		if (getOrder()<1)
			return "{empty domain}";
		StringBuffer sb = new StringBuffer("{"+getName(0));
		for (int i=1; i<getOrder(); i++) {
			sb.append(", ").append(getName(i));
		}
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Get the interval for the given bin.
	 * @param bin	the bin to query for.
	 * @return		the interval for the bin.
	 */
	public double[] getIntervals(int bin) {
		return discretizationFilter.getIntervals(bin);
	}
	
	/**
	 * Get an example continuous value for the specified discretized value.
	 * @param bin the discretized value.
	 * @return a continuous value.
	 */
	public double getExampleValue(int bin) {
		return discretizationFilter.getExampleValue(bin);
	}
}
