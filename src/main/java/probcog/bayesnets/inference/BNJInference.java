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
package probcog.bayesnets.inference;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.exception.ProbCogException;

import java.lang.reflect.InvocationTargetException;

import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.DiscreteEvidence;

/**
 * General wrapper for BNJ inference algorithms.
 * @author Dominik Jain
 */
public class BNJInference extends Sampler {

	Class<? extends edu.ksu.cis.bnj.ver3.inference.Inference> algorithmClass;
	
	public BNJInference(BeliefNetworkEx bn, Class<? extends edu.ksu.cis.bnj.ver3.inference.Inference> algoClass) throws ProbCogException {
		super(bn);
		this.algorithmClass = algoClass;
	}

	@Override
	public void _infer() throws ProbCogException {
		// set evidence
		for(int i = 0; i < evidenceDomainIndices.length; i++)
			if(evidenceDomainIndices[i] != -1)
				nodes[i].setEvidence(new DiscreteEvidence(evidenceDomainIndices[i]));
		
		// run inference
		edu.ksu.cis.bnj.ver3.inference.Inference algo;
		try {
			algo = algorithmClass.getDeclaredConstructor().newInstance();
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new ProbCogException(e);
		}
		algo.run(bn.bn);
		
		// retrieve results
		SampledDistribution dist = createDistribution();
		for(int i = 0; i < nodes.length; i++) {
			CPF cpf = algo.queryMarginal(nodes[i]);
			for(int j = 0; j < dist.values[i].length; j++)
				dist.values[i][j] = cpf.getDouble(j);
		}
		dist.Z = 1.0;
		dist.steps = 1;
		dist.trials = 1;
		((ImmediateDistributionBuilder)distributionBuilder).setDistribution(dist);

		// remove evidence
		bn.removeAllEvidences();
	}
	
	protected IDistributionBuilder createDistributionBuilder() {
		return new ImmediateDistributionBuilder();
	}	
}
