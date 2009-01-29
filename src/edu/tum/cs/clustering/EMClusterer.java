package edu.tum.cs.clustering;

import weka.clusterers.EM;

public class EMClusterer extends BasicClusterer<weka.clusterers.EM> {

	public EMClusterer(EM clusterer) {
		super(clusterer);
	}
	
	public EMClusterer() throws Exception {
		super(new EM());
		clusterer.setNumClusters(-1);
	}
}
