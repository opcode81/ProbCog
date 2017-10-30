/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
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
package probcog.clustering.multidim;

import probcog.exception.ProbCogException;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Basic clustering for one-dimensional (double) data points
 * @author Dominik Jain
 *
 * @param <TClusterer>  the underlying weka clustering class
 */
public class MultiDimClusterer<TClusterer extends weka.clusterers.Clusterer> {
	protected Attribute[] attrs;
	protected TClusterer clusterer;
	protected Instances instances;
	protected int dimensions;

	public MultiDimClusterer(TClusterer clusterer, int dimensions) {		
		this.clusterer = clusterer;
		FastVector attribs = new FastVector(dimensions);
		for(int i = 0; i < dimensions; i++) 
			attribs.addElement(new Attribute(String.format("v%d", i)));		
		instances = new Instances("foo", attribs, 100);				
	}
	
	public void addInstance(double[] v) {
		/*Instance inst = new Instance(attrs.length);
		for(int i = 0; i < attrs.length; i++)
			inst.setValue(attrs[i], v[i]);*/
		instances.add(new Instance(1.0, v));
	}
	
	public void buildClusterer() throws ProbCogException {
		try {
			clusterer.buildClusterer(instances);
		}
		catch (Exception e) {
			throw new ProbCogException(e);
		}
	}
	
	public int classify(double[] v) throws ProbCogException {
		Instance inst = new Instance(1.0, v);
		try {
			return clusterer.clusterInstance(inst);
		}
		catch (Exception e) {
			throw new ProbCogException(e);
		}
	}
	
	public TClusterer getWekaClusterer() {
		return clusterer;
	}
	
	public int numberOfClusters() throws ProbCogException {
		try {
			return clusterer.numberOfClusters();
		}
		catch (Exception e) {
			throw new ProbCogException(e);
		}
	}
}
