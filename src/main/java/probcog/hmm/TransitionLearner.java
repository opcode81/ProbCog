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

/**
 * @author Dominik Jain
*/
public class TransitionLearner {
	protected double[][] A;
	
	public TransitionLearner(int numStates, boolean usePseudoCounts) {
		A = new double[numStates][numStates];
		if(usePseudoCounts) {
			double pseudo = 1.0;
			for(int i = 0; i < numStates; i++) {
				Arrays.fill(A[i], pseudo);
			}
		}		
	}
	
	public void learn(int i, int j) {
		A[i][j] += 1.0;		
	}
	
	public double[][] finish() {
		// normalize
		int numStates = A[0].length;
		for(int i = 0; i < numStates; i++) {
			double sum = 0;
			for(int j = 0; j < numStates; j++)
				sum += A[i][j];
			for(int j = 0; j < numStates; j++)
				A[i][j] /= sum;			
		}
		return A;
	}
}
