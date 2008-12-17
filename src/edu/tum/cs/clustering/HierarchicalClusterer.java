package edu.tum.cs.clustering;

import weka.clusterers.Cobweb;

public class HierarchicalClusterer extends edu.tum.cs.clustering.BasicClusterer<Cobweb> {

	public HierarchicalClusterer() {
		super(new Cobweb());
	}
	
	public HierarchicalClusterer(Cobweb clusterer) {
		super(clusterer);
	}

}
