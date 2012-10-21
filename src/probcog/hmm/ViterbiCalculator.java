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

import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.Observation;
import be.ac.ulg.montefiore.run.jahmm.Opdf;


/**
 * This class can be used to compute the most probable state sequence matching
 * a given observation sequence (given an HMM).
 * @author Dominik Jain
 */
public class ViterbiCalculator<O extends Observation>
{	
	/**
	 * delta[t][i] is the maximum probability of being in state i at time t,
	 * i.e. the probability of the most likely path leading to state i at time t 
	 */
	protected Vector<double[]> delta;	
	private Vector<int[]> psy;	
	protected Hmm<O> hmm;
	protected int step = 0;
	protected double viterbiPathLogProb;
	
	public  ViterbiCalculator(Hmm<O> hmm) {
		delta = new Vector<double[]>();
		psy = new Vector<int[]>();		
		this.hmm = hmm;
	}
	
	/**
	 * goes one step forward
	 * @param o current observation
	 * @return log probability of current Viterbi path (most likely sequence)
	 */
	public double step(O o) {
		double best = Double.NEGATIVE_INFINITY;		
		
		if(step == 0) {
		
			double[] d = new double[hmm.nbStates()];
			for(int i = 0; i < hmm.nbStates(); i++) {
				d[i] = Math.log(hmm.getPi(i));
				Opdf<O> opdf = hmm.getOpdf(i);
				double pObs = opdf.probability(o);
				d[i] += Math.log(pObs);
				best = Math.max(d[i], best); 
			}
			delta.add(d);
			
			viterbiPathLogProb = best;			
		}
		else {
			double[] curDelta = new double[hmm.nbStates()];
			int[] curPsy = new int[hmm.nbStates()];
			for(int j = 0; j < hmm.nbStates(); j++) {				
				curDelta[j] = computeStep(o, j, curPsy);
				best = Math.max(curDelta[j], best);
			}				
			delta.add(curDelta);
			psy.add(curPsy);
			
			viterbiPathLogProb = best;
		}
		
		++step;
		
		return viterbiPathLogProb;
	}
	
	
	/**
	 * find log probability of most likely path to state j given current observation 
	 */
	protected double computeStep(O o, int j, int[] curPsy) 
	{
		double maxDelta = Double.NEGATIVE_INFINITY;
		int max_psy = 0;
		double[] prev = delta.lastElement();
		
		for (int i = 0; i < hmm.nbStates(); i++) {
			double transitionProb = hmm.getAij(i, j);
			double transitionLP = Math.log(transitionProb);
			double thisDelta = prev[i] + transitionLP;
			
			if(thisDelta > maxDelta) {
				maxDelta = thisDelta; 
				max_psy = i; // most likely path is from state i
			}
		}
		
		double observationLP = Math.log(hmm.getOpdf(j).probability(o));
		curPsy[j] = max_psy;
		
		return maxDelta + observationLP;
	}
	
	
	public void run(Iterable<? extends O> observations) {
		int i = 0;
		for(O o : observations) {
			System.out.printf("Viterbi step %d\r", i++);
			step(o);
		}
		System.out.println();
	}
	
	/**
	 * Returns the logarithm of the probability of the
	 * most likely state path and the observations that have been passed
	 *
	 * @return <code>ln(P[O,S|H])</code> where <code>O</code> is the given
	 *         observation sequence, <code>H</code> the given HMM and 
	 *         <code>S</code> the most likely state sequence of this observation
	 *         sequence given this HMM.
	 */
	public double getViterbiPathLogProbability() {
		return viterbiPathLogProb;
	}
	
	
	/**
	 * Returns a (clone of) the array containing the computed most likely
	 * state sequence.
	 *
	 * @return The state sequence; the i-th value of the array is the index
	 *         of the i-th state of the state sequence.
	 */
	public List<Integer> getViterbiPath() 
	{
		List<Integer> ret = new LinkedList<Integer>();
		
		double lnProbability = Double.NEGATIVE_INFINITY;
		Integer best = null;
		double[] finalProbs = delta.lastElement();
		for (int i = 0; i < hmm.nbStates(); i++) {
			double thisProbability = finalProbs[i];			
			if (thisProbability > lnProbability) {
				best = i;	
				lnProbability = thisProbability;
			}
		}
		
		ret.add(best);
		for(int i = psy.size()-1; i >= 0; i--) {
			best = psy.get(i)[best];
			ret.add(0, best);
		}
		return ret;
	}
}
