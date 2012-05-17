package probcog.clustering;

import weka.clusterers.Cobweb;

public class HierarchicalClusterer extends probcog.clustering.BasicClusterer<Cobweb> {

	public HierarchicalClusterer() {
		super(new Cobweb());
	}
	
	public HierarchicalClusterer(Cobweb clusterer) {
		super(clusterer);
	}

}
