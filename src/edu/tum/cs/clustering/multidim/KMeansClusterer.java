/*
 * Created on Jun 2, 2010
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.clustering.multidim;

import weka.clusterers.SimpleKMeans;

public class KMeansClusterer extends MultiDimClusterer<SimpleKMeans> {

	public KMeansClusterer(SimpleKMeans clusterer, int dimensions, int k) throws Exception {
		super(clusterer, dimensions);
		clusterer.setNumClusters(k);
	}
}
