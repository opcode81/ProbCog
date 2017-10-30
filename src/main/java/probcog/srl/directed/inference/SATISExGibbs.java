/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
package probcog.srl.directed.inference;

import java.util.Collection;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.inference.GibbsSampling;
import probcog.bayesnets.inference.SATIS_BSampler;
import probcog.bayesnets.inference.Sampler;
import probcog.bayesnets.inference.WeightedSample;
import probcog.exception.ProbCogException;
import probcog.logic.sat.SampleSAT;
import probcog.srl.directed.bln.GroundBLN;
import probcog.srl.directed.bln.coupling.VariableLogicCoupling;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

/**
 * SAT-IS Extended (with hard constraints from CPDs) which, for every SAT-IS step, 
 * adds a number of Gibbs steps to explore the subspace given by the sample;
 * the samples are weighted the same as the original SAT-IS sample 
 * @author Dominik Jain
 */
public class SATISExGibbs extends SATISEx {
	protected int gibbsSteps = 9;

	public SATISExGibbs(GroundBLN bln) throws ProbCogException {
		super(bln);
		this.paramHandler.add("gibbsSteps", "setNumGibbsSteps");
	}
	
	public void setNumGibbsSteps(int n) {
		gibbsSteps = n;
	}
	
	@Override
	protected Sampler getSampler() throws ProbCogException {
		initSATSampler();
		return new SATIS_BSampler_Gibbs(gbln.getGroundNetwork(), ss, gbln.getCoupling(), determinedVars);
	}	
	
	public class SATIS_BSampler_Gibbs extends SATIS_BSampler {

		public GibbsSampling gibbsSampler;
		
		public SATIS_BSampler_Gibbs(BeliefNetworkEx bn, SampleSAT sat, VariableLogicCoupling coupling, Collection<BeliefNode> determinedVars) throws ProbCogException {
			super(bn, sat, coupling, determinedVars);
			gibbsSampler = new GibbsSampling(gbln.getGroundNetwork());
		}
		
		public void onAddedSample(WeightedSample s) throws ProbCogException {
			for(int i = 0; i < gibbsSteps; i++) {
				System.out.println(s.weight);
				double p = gibbsSampler.gibbsStep(this.evidenceDomainIndices, s);
				s.weight = this.bn.getWorldProbability(s.nodeDomainIndices) / p;				
				addSample(s);
				currentStep++;
			}
		}
	}

}
