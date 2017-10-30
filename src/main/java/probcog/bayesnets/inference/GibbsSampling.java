/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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

import java.util.HashMap;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.exception.ProbCogException;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.util.Stopwatch;

/**
 * Gibbs Sampling MCMC inference.
 * @author Dominik Jain
 */
public class GibbsSampling extends Sampler {
	int[] nodeOrder;	
	HashMap<BeliefNode, BeliefNode[]> children;
	
	public GibbsSampling(BeliefNetworkEx bn) throws ProbCogException {
		super(bn);
		children = new HashMap<BeliefNode, BeliefNode[]>();
		for(int i = 0; i < nodes.length; i++) {
			children.put(nodes[i], bn.bn.getChildren(nodes[i]));
		}
		nodeOrder = bn.getTopologicalOrder();
	}
	
	public void _infer() throws ProbCogException {
		Stopwatch sw = new Stopwatch();

		// get initial setting with non-zero evidence probability
		out.println("initial setting...");
		WeightedSample s = bn.getWeightedSample(nodeOrder, evidenceDomainIndices, generator);
		if(s == null)
			throw new ProbCogException("Could not find an initial state with non-zero probability in given number of trials.");
		
		// do Gibbs sampling
		out.println("Gibbs sampling...");
		sw.start();		
		// - get a bunch of samples
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				out.println("  step " + i);
			gibbsStep(evidenceDomainIndices, s);
			s.trials = 1;
			s.weight = 1;
			addSample(s);
		}

		sw.stop();
		report(String.format("time taken: %.2fs (%.4fs per sample)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples));
	}
	
	public double gibbsStep(int[] evidenceDomainIndices, WeightedSample s) {
		double p = 1.0;
		// resample all of the (non-evidence) nodes
		for(int j = 0; j < nodes.length; j++)  {
			// skip evidence nodes
			if(evidenceDomainIndices[j] != -1)
				continue;
			// initialize
			BeliefNode n = nodes[j];
			Discrete dom = (Discrete)n.getDomain();
			int domSize = dom.getOrder();
			double[] distribution = new double[domSize];
			// for the current node, calculate a value for each setting
			for(int d = 0; d < domSize; d++) {
				s.nodeDomainIndices[j] = d;
				// consider the probability of the setting given the node's parents
				double value = getCPTProbability(n, s.nodeDomainIndices);
				// consider the probability of the children's settings given the respective parents					
				for(BeliefNode child : children.get(n)) {
					value *= getCPTProbability(child, s.nodeDomainIndices);
				}			
				distribution[d] = value;
			}		
			double sum = 0;
			for(int i = 0; i < distribution.length; i++)
				sum += distribution[i];
			s.nodeDomainIndices[j] = sample(distribution, sum, generator);
			p = distribution[s.nodeDomainIndices[j]] / sum;
		}
		return p;
	}
}
