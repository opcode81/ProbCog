/*******************************************************************************
 * Copyright (C) 2012 Dominik Jain.
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
package probcog.clustering;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Basic clustering for one-dimensional (double) data points.
 * @author Dominik Jain
 *
 * @param <TClusterer>  the underlying weka clustering class
 */
public class BasicClusterer<TClusterer extends weka.clusterers.Clusterer> {
	protected Attribute attrValue;
	protected TClusterer clusterer;
	protected Instances instances;

	public BasicClusterer(TClusterer clusterer) {
		attrValue = new Attribute("value");
		FastVector attribs = new FastVector(1);
		attribs.addElement(attrValue);		
		instances = new Instances("foo", attribs, 100);
		this.clusterer = clusterer;		
	}
	
	public void addInstance(double value) {
		Instance inst = new Instance(1);
		inst.setValue(attrValue, value);
		instances.add(inst);
	}
	
	public void buildClusterer() throws Exception {
		clusterer.buildClusterer(instances);
	}
	
	public int classify(double value) throws Exception {
		Instance inst = new Instance(1);
		inst.setValue(attrValue, value);
		return clusterer.clusterInstance(inst);
	}
	
	public TClusterer getWekaClusterer() {
		return clusterer;
	}
}
