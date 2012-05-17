/*
 * Created on Jun 7, 2010
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.hmm;

import java.util.Arrays;

public class DistributionLearner {
	protected double[] dist;
	
	public DistributionLearner(int numEntries, boolean usePseudoCounts) {
		dist = new double[numEntries];
		if(usePseudoCounts) {
			double pseudo = 1.0;
			Arrays.fill(dist, pseudo);
		}		
	}
	
	public void learn(int i) {
		dist[i] += 1.0;		
	}
	
	public double[] finish() {
		// normalize
		double sum = 0;
		for(int i = 0; i < dist.length; i++) 			
			sum += dist[i];
		for(int j = 0; j < dist.length; j++)
			dist[j] /= sum;		
		return dist;
	}
}
