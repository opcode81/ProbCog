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

import java.util.List;


import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.Observation;


/**
 * The forward algorithm for basic/standard HMMs
 * @author Dominik Jain
 */
public class ForwardCalculator<O extends Observation> implements IObservationModel<O>
{	
	protected double[] bel;	
	protected Hmm<O> hmm;
	protected int step = 0;
	
	public ForwardCalculator(Hmm<O> hmm) {
		this.hmm = hmm;
		bel = new double[hmm.nbStates()];
	}
	
	public double step(O o) {
		double obsProb = 0.0;
		if(step == 0) {		
			for(int i = 0; i < hmm.nbStates(); i++) {
				bel[i] = hmm.getPi(i) * hmm.getOpdf(i).probability(o);
				obsProb += bel[i];
			}
		}
		else {
			double[] bel2 = new double[hmm.nbStates()];
			for(int j = 0; j < hmm.nbStates(); j++) {
				for(int i = 0; i < hmm.nbStates(); i++) {				
					double pTrans = hmm.getAij(i, j);
					bel2[j] += bel[i] * pTrans;					
				}
				bel2[j] *= hmm.getOpdf(j).probability(o);
				obsProb += bel2[j];
			}
			bel = bel2;
		}
		
		// normalize beliefs
		for(int i = 0; i < hmm.nbStates(); i++)
			bel[i] /= obsProb;
		
		++step;
		
		return obsProb;
	}
		
	public void run(List<? extends O> observations) {
		for(O o : observations)
			step(o);
	}

	@Override
	public double getObservationProbability(O observation) {
		return step(observation);
	}
	
}
