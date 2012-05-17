/*
 * Created on Jun 2, 2010
 */
package probcog.hmm;

import java.util.Arrays;

/**
 * @author jain
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
