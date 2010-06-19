/*
 * Created on Jun 2, 2010
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.clustering.multidim;

import weka.clusterers.EM;

public class EMClusterer extends MultiDimClusterer<EM> {

	public EMClusterer(EM clusterer, int dimensions) throws Exception {
		super(clusterer, dimensions);
		//clusterer.setNumClusters(k);
	}
}
