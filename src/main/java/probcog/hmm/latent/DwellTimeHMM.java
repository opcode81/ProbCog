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
package probcog.hmm.latent;

import java.util.Collection;

import be.ac.ulg.montefiore.run.distributions.GaussianDistribution;
import be.ac.ulg.montefiore.run.jahmm.Observation;
import be.ac.ulg.montefiore.run.jahmm.ObservationReal;
import be.ac.ulg.montefiore.run.jahmm.OpdfGaussian;

/**
 * Hidden semi-Markov model where state transition probabilities depend
 * on the dwell time, i.e. the number of time steps already spent in a state
 * @author Dominik Jain
 */
public abstract class DwellTimeHMM<O extends Observation> implements IDwellTimeHMM<O> {
	protected double[][] A;
	protected double[] pi;
	protected GaussianDistribution[] dwellTimeDist;
	protected Integer numStates;

	public DwellTimeHMM(int numStates) {
		init(numStates);
	}
	
	/**
	 * constructs a completely uninitialized HMM
	 */
	public DwellTimeHMM() {
		numStates = null;
	}
	
	protected void init(int numStates) {
		if(numStates <= 0)
			throw new IllegalArgumentException("Number of states must be >= 0.");
		A = new double[numStates][numStates];		
		pi = new double[numStates];		
		dwellTimeDist = new GaussianDistribution[numStates];
		this.numStates = numStates;
	}
	
	@Override
	public Integer getNumStates() {
		return numStates;
	}
	
	/**
	 * (re-)initializes this HMM for the given number of states
	 * @param numStates
	 * @throws IllegalAccessException 
	 */
	@Override
	public void setNumStates(int numStates) throws IllegalAccessException {
		if(this.numStates != null)
			throw new IllegalAccessException("Cannot set number of states in model which was constructed with known number of states");
		init(numStates);
	}
	
	public double getPi(int state) {
		return pi[state];
	}
	
	public double getDwellProbability(int label, int dwellTime) {
		return 1 - dwellTimeDist[label].cdf(dwellTime);
	}
	
	public double getTransitionProbability(int from, int dwellTime, int to) {
		double pDwell = 1 - dwellTimeDist[from].cdf(dwellTime);
		return (1.0-pDwell) * A[from][to];
	}	

	public void setA(double[][] A) {
		this.A = A;
	}
	
	public void setPi(double[] pi) {
		this.pi = pi;
	}
	
	public void learnDwellTimeDistribution(int state, Collection<ObservationReal> times) {
		OpdfGaussian pdf = new OpdfGaussian();
		if(times.size() == 1) {
			System.err.println("Only 1 example for length, therefore adding additional items +/- 10");
			ObservationReal r = times.iterator().next();
			times.add(new ObservationReal(r.value+10));
			times.add(new ObservationReal(r.value-10));
		}
		pdf.fit(times);
		double var = pdf.variance();
		dwellTimeDist[state] = new GaussianDistribution(pdf.mean(), var);
	}	
}
