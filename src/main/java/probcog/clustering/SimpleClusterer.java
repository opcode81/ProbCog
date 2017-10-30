/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.clustering;
import java.util.Arrays;

import probcog.exception.ProbCogException;
import weka.clusterers.SimpleKMeans;

/**
 * The K-Means clusterer as implemented in WEKA with some additional functionality.
 * @author Dominik Jain
 */
public class SimpleClusterer extends BasicClusterer<SimpleKMeans> {
	
	int[] clusterIndex2sortedClusterIndex;
	
	public SimpleClusterer(SimpleKMeans clusterer) {
		super(clusterer);		
	}
	
	public SimpleClusterer() {
		this(new SimpleKMeans());
	}
	
	public void buildClusterer(int numClusters) throws ProbCogException {
		if(numClusters != 0)
			setNumClusters(numClusters);		
		buildClusterer();
	}
	
	@Override
	public void buildClusterer() throws ProbCogException {
		try {
			super.buildClusterer();
		}
		catch (Exception e) {
			throw new ProbCogException(e);
		}
		clusterIndex2sortedClusterIndex = getSortedCentroidIndices();
	}
	
	public void setNumClusters(int n) throws ProbCogException {
		try {
			clusterer.setNumClusters(n);
		}
		catch (Exception e) {
			throw new ProbCogException(e);
		}
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
	 * @throws ProbCogException 
	 */
	@Override
	public int classify(double value) throws ProbCogException {
		int i;
		try {
			i = super.classify(value);
		}
		catch (Exception e) {
			throw new ProbCogException(e);
		}
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
