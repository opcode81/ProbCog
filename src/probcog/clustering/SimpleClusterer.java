package probcog.clustering;
import java.util.Arrays;

import weka.clusterers.SimpleKMeans;

/**
 * K-Means clusterer
 * @author jain
 */
public class SimpleClusterer extends BasicClusterer<SimpleKMeans> {
	
	int[] clusterIndex2sortedClusterIndex;
	
	public SimpleClusterer(SimpleKMeans clusterer) {
		super(clusterer);		
	}
	
	public SimpleClusterer() {
		this(new SimpleKMeans());
	}
	
	public void buildClusterer(int numClusters) throws Exception {
		if(numClusters != 0)
			setNumClusters(numClusters);		
		buildClusterer();
	}
	
	@Override
	public void buildClusterer() throws Exception {
		super.buildClusterer();
		clusterIndex2sortedClusterIndex = getSortedCentroidIndices();
	}
	
	public void setNumClusters(int n) throws Exception {
		clusterer.setNumClusters(n);
	}
	
	public double[] getCentroids() {
		return clusterer.getClusterCentroids().attributeToDoubleArray(0);
	}
	
	public double[] getStdDevs() {
		return clusterer.getClusterStandardDevs().attributeToDoubleArray(0);
	}
	
	/**
	 * classifies the given value
	 * @return *not* the index of the actual cluster but the index into a list of clusters sorted in ascending order of centroid mean 
	 * @throws Exception 
	 */
	@Override
	public int classify(double value) throws Exception {
		int i = super.classify(value);
		return clusterIndex2sortedClusterIndex[i];
	}
	
	/**
	 * 
	 * @return an array of indices that maps the index of a centroid to its position in the sorted
	 * list of centroids  
	 */
	protected int[] getSortedCentroidIndices() {
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
}
