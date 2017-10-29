/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
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
