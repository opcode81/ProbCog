package probcog.clustering.multidim;

import java.lang.reflect.InvocationTargetException;

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
public class MultiDimClusterer<TClusterer extends weka.clusterers.Clusterer> {
	protected Attribute[] attrs;
	protected TClusterer clusterer;
	protected Instances instances;
	protected int dimensions;

	public MultiDimClusterer(TClusterer clusterer, int dimensions) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {		
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
	
	public void buildClusterer() throws Exception {
		clusterer.buildClusterer(instances);
	}
	
	public int classify(double[] v) throws Exception {
		Instance inst = new Instance(1.0, v);
		return clusterer.clusterInstance(inst);
	}
	
	public TClusterer getWekaClusterer() {
		return clusterer;
	}
}
