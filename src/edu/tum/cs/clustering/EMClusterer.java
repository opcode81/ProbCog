package edu.tum.cs.clustering;

import weka.clusterers.EM;

/**
 * Expectation Maximization clustering as implemented in WEKA (can automatically determine a suitable number of clusters)
 * @author jain
 *
 */
public class EMClusterer extends BasicClusterer<weka.clusterers.EM> {

	public EMClusterer(EM clusterer) {
		super(clusterer);
	}
	
	public EMClusterer() throws Exception {
		super(new EM());
		clusterer.setNumClusters(-1);
	}
}
