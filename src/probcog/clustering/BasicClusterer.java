package probcog.clustering;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 * basic clustering for one-dimensional (double) data points
 * @author jain
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
