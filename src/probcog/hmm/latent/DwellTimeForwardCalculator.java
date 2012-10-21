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

import java.util.HashMap;
import java.util.Map;

import probcog.hmm.IObservationModel;


/**
 * @author Dominik Jain
 */
public class DwellTimeForwardCalculator<O> implements IObservationModel<O>
{			
	protected IDwellTimeHMM<O> hmm;	
	protected Map<State,Belief> bel;	
	protected int step = 0;
	
	public class Belief {		
		public State s;
		double belief;
		public IObservationModel<O> obsModel;
		
		/**
		 * constructs an initial belief
		 * @param label
		 * @param observation
		 */
		public Belief(int label, O observation) {
			s = new State(label, 0);
			obsModel = hmm.getObservationModel(label);
			belief = hmm.getPi(label);
			belief *= obsModel.getObservationProbability(observation);
		}
		
		private Belief() {}
		
		public Belief proceed(int label, O observation) {
			Belief p2 = new Belief();
			if(label == -1) { // staying in same segment
				p2.s = new State(s.label, s.dwellTime+1);
				p2.obsModel = this.obsModel;
			}
			else { // switching to another segment
				p2.s = new State(label, 0);
				p2.obsModel = hmm.getObservationModel(label);
			}
						
			// update probabilities
			double pTrans = this.s.getTransitionProbability(hmm, label);
			double pObs = p2.obsModel.getObservationProbability(observation);				
			p2.belief = this.belief * pTrans * pObs;
			
			return p2;
		}
	}

	
	/**
	 * Computes the most likely state sequence matching an observation
	 * sequence given an HMM.
	 *
	 * @param hmm A Hidden Markov Model;
	 * @param oseq An observations sequence.
	 */
	public DwellTimeForwardCalculator(IDwellTimeHMM<O> hmm) {
		this.hmm = hmm;		
	}
	
	public double step(O o) {
		double Z = 0.0;
		int numStates = hmm.getNumStates();
		
		if(step == 0) {		
			
			bel = new HashMap<State,Belief>();
			for(int i = 0; i < numStates; i++) {
				Belief b = new Belief(i, o);
				if(b.belief != 0.0) {
					bel.put(b.s, b);
					Z += b.belief;
				}
			}
			
		}
		else {
			
			HashMap<State,Belief> bel2 = new HashMap<State,Belief>();
			for(Belief b1 : bel.values()) {
				for(int i = -1; i < numStates; i++) {
					Belief b2 = b1.proceed(i, o);
					if(b2.belief != 0.0) {
						Belief entry = bel2.get(b2.s);
						if(entry != null)
							entry.belief += b2.belief;
						else
							bel2.put(b2.s, b2);
						Z += b2.belief;
					}
				}
			}
			bel = bel2;
			
		}
		
		// normalize beliefs
		for(Belief b : bel.values())
			b.belief /= Z;
		
		++step;
		
		return Z;
	}

	@Override
	public double getObservationProbability(O observation) {
		return step(observation);
	}
}
