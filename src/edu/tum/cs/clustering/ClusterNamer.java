package edu.tum.cs.clustering;
import java.util.Arrays;

import umontreal.iro.lecuyer.charts.SSJCategorySeriesCollection;
import weka.clusterers.Clusterer;
import weka.clusterers.SimpleKMeans;
import weka.core.Instances;

/**
 * an interface for use with DomainLearner that contains a function that, when given
 * a WEKA clusterer, returns an array of cluster names
 * @author Dominik Jain
 */
public interface ClusterNamer<Cl extends Clusterer> {
	public String[] getNames(Cl clusterer) throws Exception;
	
	/**
	 * the most basic cluster namer, which simply adds a prefix to each cluster index
	 * @author jain
	 */
	public static class SimplePrefix implements ClusterNamer<Clusterer> {
		protected String prefix;
		
		public SimplePrefix(String prefix) {
			this.prefix = prefix;
		}
		
		public String[] getNames(Clusterer clusterer) throws Exception {
			int n = clusterer.numberOfClusters();
			String[] names = new String[n];
			for(Integer i = 0; i < n; i++)
				names[i] = prefix + i.toString();
			return names;
		}
	}
	
	/**
	 * a basic cluster namer that simply returns a fixed list of predetermined names
	 * @author jain
	 */
	public static class Fixed implements ClusterNamer<Clusterer> {
		protected String[] names;
		
		public Fixed(String[] names) {
			this.names = names;
		}
		
		public String[] getNames(Clusterer clusterer) throws Exception {
			if(clusterer.numberOfClusters() != names.length)
				throw new Exception("Number of clusters does not match number of names.");
			return names;
		}
	}
	
	/**
	 * a K-Means cluster namer which simply returns the string "~E +/- S" for each cluster, where
	 * E is the expected value and S the standard deviation of the cluster. 
	 * @author Dominik Jain
	 */
	public static class MeanStdDev implements ClusterNamer<SimpleKMeans> {
		public String[] getNames(SimpleKMeans clusterer) {
			int numClusters = clusterer.getNumClusters();
			String[] ret = new String[numClusters];
			Instances centroids = clusterer.getClusterCentroids();
			Instances stdDevs = clusterer.getClusterStandardDevs();
			for(int i = 0; i < numClusters; i++)
				ret[i] = String.format("~%.2f +/- %.2f", centroids.instance(i).value(0), stdDevs.instance(i).value(0));
			return ret;
		}
	}
	
	/**
	 * a K-Means cluster namer, which returns the range of values (i.e. an interval), formatted
	 * in a string, for each
	 * cluster by calculating the intersections of the Gaussian distributions 
	 * @author Dominik Jain
	 */
	public static class Intervals implements ClusterNamer<SimpleKMeans> {
		/**
		 * calculates the intersection of two Gaussian distributions
		 * @param e1	the expected value of the first distribution
		 * @param s1	the standard deviation of the first distribution
		 * @param e2	the expected value of the second distribution
		 * @param s2	the standard deviation of the second distribution
		 * @return		the x-coordinate of the intersection
		 */
		public static double getIntersection(double e1, double s1, double e2, double s2) {
			if(s2 == 0)
				return e2;
			if(s1 == s2)
				return (e1 + e2) / 2;
			double r1 = 1.0/2/(s1*s1-s2*s2)*(2*s1*s1*e2-2*s2*s2*e1+2*Math.sqrt(-2*s1*s1*e2*s2*s2*e1+s1*s1*s2*s2*e1*e1-2*s1*s1*s1*s1*Math.log(s2/s1)*s2*s2+s2*s2*s1*s1*e2*e2+2*s2*s2*s2*s2*Math.log(s2/s1)*s1*s1));
			if((e1 <= r1 && r1 <= e2) || (e2 <= r1 && r1 <= e1))
				return r1;			
			double r2 = 1.0/2/(s1*s1-s2*s2)*(2*s1*s1*e2-2*s2*s2*e1-2*Math.sqrt(-2*s1*s1*e2*s2*s2*e1+s1*s1*s2*s2*e1*e1-2*s1*s1*s1*s1*Math.log(s2/s1)*s2*s2+s2*s2*s1*s1*e2*e2+2*s2*s2*s2*s2*Math.log(s2/s1)*s1*s1));
			return r2;
		}
		
		public String[] getNames(SimpleKMeans clusterer) {
			int numClusters = clusterer.getNumClusters();
			String[] ret = new String[numClusters];
			double[] centroids = clusterer.getClusterCentroids().attributeToDoubleArray(0);
			double[] stdDevs = clusterer.getClusterStandardDevs().attributeToDoubleArray(0);
			double[] sortedCentroids = centroids.clone();
			Arrays.sort(sortedCentroids);
			int[] sortOrder = new int[numClusters];
			for(int i = 0; i < numClusters; i++) 
				for(int j = 0; j < numClusters; j++)
					if(centroids[j] == sortedCentroids[i])
						sortOrder[i] = j;
			boolean lastNoInterv = false;
			for(int i = 0; i < numClusters; i++) {
				int idx = sortOrder[i];
				if(stdDevs[idx] == 0.0) { // no deviation -> no range
					ret[idx] = String.format("%.2f", centroids[idx]);
					continue;
				}
				if(i == 0) { // no left neighbour
					ret[idx] = String.format("< %.2f (~%.2f)", getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[1]], stdDevs[sortOrder[1]]),  centroids[idx]);
					continue;
				}
				if(i == numClusters-1) { // no right neighbour
					ret[idx] = String.format("> %.2f (~%.2f)", getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[i-1]], stdDevs[sortOrder[i-1]]),  centroids[idx]);
					continue;					
				}
				double left = getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[i-1]], stdDevs[sortOrder[i-1]]);
				double right = getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[i+1]], stdDevs[sortOrder[i+1]]);
				ret[idx] = String.format("%.2f - %.2f (~%.2f)", left, right, centroids[idx]);
			}
			return ret;
		}
	}
	
	/**
	 * variant of Intervals that produces names that are compatible with SRL databases 
	 * @author jain
	 */
	public static class IntervalsPlain implements ClusterNamer<SimpleKMeans> {
		
		protected static String strFloat(double f) {
			String s = String.format("%.2f", f);
			return s.replace('-', 'm').replace(",", "p");			
		}
		
		public String[] getNames(SimpleKMeans clusterer) {
			int numClusters = clusterer.getNumClusters();
			String[] ret = new String[numClusters];
			double[] centroids = clusterer.getClusterCentroids().attributeToDoubleArray(0);
			double[] stdDevs = clusterer.getClusterStandardDevs().attributeToDoubleArray(0);
			double[] sortedCentroids = centroids.clone();
			Arrays.sort(sortedCentroids);
			int[] sortOrder = new int[numClusters];
			for(int i = 0; i < numClusters; i++) 
				for(int j = 0; j < numClusters; j++)
					if(centroids[j] == sortedCentroids[i])
						sortOrder[i] = j;
			boolean lastNoInterv = false;
			for(int i = 0; i < numClusters; i++) {
				int idx = sortOrder[i];
				if(stdDevs[idx] == 0.0) { // no deviation -> no range
					ret[idx] = String.format("C_%s", strFloat(centroids[idx]));
					continue;
				}
				if(i == 0) { // no left neighbour
					ret[idx] = String.format("C_lt_%s_%s", strFloat(Intervals.getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[1]], stdDevs[sortOrder[1]])), strFloat(centroids[idx]));
					continue;
				}
				if(i == numClusters-1) { // no right neighbour
					ret[idx] = String.format("C_gt_%s_%s", strFloat(Intervals.getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[i-1]], stdDevs[sortOrder[i-1]])),  strFloat(centroids[idx]));
					continue;					
				}
				double left = Intervals.getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[i-1]], stdDevs[sortOrder[i-1]]);
				double right = Intervals.getIntersection(centroids[idx], stdDevs[idx], centroids[sortOrder[i+1]], stdDevs[sortOrder[i+1]]);
				ret[idx] = String.format("C_%s_to_%s_%s", strFloat(left), strFloat(right), strFloat(centroids[idx]));
			}
			return ret;
		}
	}

}

