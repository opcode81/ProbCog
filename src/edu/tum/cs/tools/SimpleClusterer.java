package edu.tum.cs.tools;
import java.util.*;
import weka.clusterers.*;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class SimpleClusterer {
	
	protected Attribute attrValue;
	protected SimpleKMeans clusterer;
	protected Instances instances;
	
	public SimpleClusterer(SimpleKMeans clusterer) {
		attrValue = new Attribute("value");
		FastVector attribs = new FastVector(1);
		attribs.addElement(attrValue);		
		instances = new Instances("foo", attribs, 100);
		this.clusterer = clusterer;		
	}
	
	public SimpleClusterer() {
		this(new SimpleKMeans());
	}
	
	public void addInstance(double value) {
		Instance inst = new Instance(1);
		inst.setValue(attrValue, value);
		instances.add(inst);
	}
	
	public SimpleKMeans buildClusterer(int numClusters) throws Exception {
		if(numClusters != 0)
			clusterer.setNumClusters(numClusters);
		clusterer.buildClusterer(instances);
		return clusterer;
	}
	
	public double[] getCentroids() {
		return clusterer.getClusterCentroids().attributeToDoubleArray(0);
	}
	
	public double[] getStdDevs() {
		return clusterer.getClusterStandardDevs().attributeToDoubleArray(0);
	}
	
	/**
	 * 
	 * @return an array of indices that maps the index of a centroid to its position in the sorted
	 * list of centroids  
	 */
	public int[] getSortedCentroidIndices() {
		// get an unsorted and a sorted version of the centroids array
		int numClusters = clusterer.getNumClusters();
		double[] values = getCentroids();
		double[] sorted_values = (double[]) values.clone();
		Arrays.sort(sorted_values);
		// get an array of indices that corresponds to the sort order
		int[] indices = new int[numClusters];
		for(int i = 0; i < numClusters; i++)
			for(int j = 0; j < numClusters; j++)
				if(sorted_values[i] == values[j])
					indices[j] = i;
		return indices;
	}
	
	public int classify(double value) throws Exception {
		Instance inst = new Instance(1);
		inst.setValue(attrValue, value);
		return clusterer.clusterInstance(inst);
	}
}
