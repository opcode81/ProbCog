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

import java.util.List;

import probcog.exception.ProbCogException;
import probcog.hmm.ForwardCalculator;
import probcog.hmm.HMM;
import probcog.hmm.Segment;

import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;
import be.ac.ulg.montefiore.run.jahmm.Opdf;
import be.ac.ulg.montefiore.run.jahmm.OpdfFactory;
import be.ac.ulg.montefiore.run.jahmm.OpdfIndependentGaussiansFactory;
import be.ac.ulg.montefiore.run.jahmm.learn.BaumWelchScaledLearner;
import edu.tum.cs.util.datastruct.ParameterMap;

/**
 * A standard HMM for use as submodel of an LDHMM
 * @author Dominik Jain
 */
public class SubHMMSimple extends HMM<ObservationVector> implements ISubHMM {

	private static final long serialVersionUID = 1L;
	
	public SubHMMSimple(int nbStates, int numSubLevels, int obsDimension) {
		super(nbStates, getOpdfFactory(obsDimension));
	}

	public SubHMMSimple(int numSubLevels, int obsDimension) {
		super(getOpdfFactory(obsDimension));
	}
	
	protected static OpdfFactory<? extends Opdf<ObservationVector>> getOpdfFactory(int obsDimension) {
		//return new OpdfMultiGaussianFactory(obsDimension);
		return new OpdfIndependentGaussiansFactory(obsDimension);
	}
	
	public void learnViaBaumWelch(List<? extends Segment<? extends ObservationVector>> s) {
		BaumWelchScaledLearner bw = new BaumWelchScaledLearner();
		Hmm<ObservationVector> hmm = bw.learn(this, s);
		
		this.pi = hmm.getPi();
		this.a = hmm.getA();
		this.opdfs = hmm.getOpdfs();
	}
	
	public void learnViaClustering(Iterable<? extends Segment<? extends ObservationVector>> s, boolean usePseudoCounts) throws ProbCogException {
		SubHMM.learnViaClustering(this, s, usePseudoCounts);
	}

	@Override
	public double getDwellProbability(int state, int dwellTime) {		
		return a[state][state];
	}

	@Override
	public double getTransitionProbability(int from, int dwellTime, int to) {
		return a[from][to];
	}

	@Override
	public void learn(List<? extends Segment<? extends ObservationVector>> s, ParameterMap learningParams) throws ProbCogException {
		if(learningParams.getBoolean("learnSubHMMViaBaumWelch"))
			learnViaBaumWelch(s);
		else
			learnViaClustering(s, learningParams.getBoolean("usePseudoCounts"));		
	}
	
	public ForwardCalculator<ObservationVector> getForwardCalculator() {
		return new ForwardCalculator<ObservationVector>(this);		
	}
}
