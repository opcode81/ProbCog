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

import java.io.PrintStream;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.inference.BasicSampledDistribution;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;

/**
 * class that allows the incremental construction of a probability distribution from (weighted) samples
 * (see {@link WeightedSample})
 * @author Dominik Jain
 *
 */
public class SampledDistribution extends BasicSampledDistribution implements Cloneable {
	/**
	 * the belief network for which we are representing a distribution
	 */
	public BeliefNetworkEx bn;
	/**
	 * values that may be used by certain algorithms to store the number of steps involved in creating the distribution
	 */
	public int steps, trials, operations;
	protected double maxWeight = 0.0;
	protected boolean debug = true;
	protected BeliefNode[] nodes;
	
	public SampledDistribution(BeliefNetworkEx bn) throws Exception {
		this.bn = bn;
		this.Z = 0.0;
		nodes = bn.bn.getNodes();
		values = new double[nodes.length][];
		for(int i = 0; i < nodes.length; i++)
			values[i] = new double[nodes[i].getDomain().getOrder()];			
	}
	
	public synchronized void addSample(WeightedSample s) {
		if(s.weight == 0.0) {
			throw new RuntimeException("Zero-weight sample was added to distribution. Precision loss?");
		}
		
		// update normalization constant and maximum weight
		Z += s.weight;		
		if(maxWeight < s.weight)
			maxWeight = s.weight;
		
		// debug info
		if(debug) {
			double prob = bn.getWorldProbability(s.nodeDomainIndices);
			/*for(int i = 0; i < nodes.length; i++) {
				System.out.printf(" %s = %s\n", nodes[i].getName(), nodes[i].getDomain().getName(s.nodeDomainIndices[i]));
			}*/
			System.out.printf("sample weight: %s (%.2f%%); max weight: %s (%.2f%%); prob: %s\n", s.weight, s.weight*100/Z, maxWeight, maxWeight*100/Z, prob);
		}
		
		// update distribution values
		for(int i = 0; 	i < s.nodeIndices.length; i++) {
			try {
				values[s.nodeIndices[i]][s.nodeDomainIndices[i]] += s.weight;
			}
			catch(ArrayIndexOutOfBoundsException e) {
				System.err.println("Error: Node " + nodes[s.nodeIndices[i]].getName() + " was not sampled correctly.");
				throw e;
			}
		}
		
		// update number of steps and trials
		trials += s.trials;
		operations += s.operations;
		steps++;
	}
	
	@Override
	public void printVariableDistribution(PrintStream out, int index) {
		BeliefNode node = nodes[index];
		out.println(node.getName() + ":");
		Discrete domain = (Discrete)node.getDomain();
		for(int j = 0; j < domain.getOrder(); j++) {
			double prob = values[index][j] / Z;
			out.println(String.format("  %.4f %s", prob, domain.getName(j)));
		}
	}
	
	public double getTrialsPerStep() {
		return (double)trials/steps;
	}
	
	@Override
	public synchronized SampledDistribution clone() throws CloneNotSupportedException {
		return (SampledDistribution)super.clone();
	}

	@Override
	public String[] getDomain(int idx) {
		return BeliefNetworkEx.getDiscreteDomainAsArray(bn.getNode(idx));
	}

	@Override
	public String getVariableName(int idx) {
		return bn.getNode(idx).getName();
	}

	@Override
	public int getVariableIndex(String name) {
		return bn.getNodeIndex(name);
	}

	public void setDebugMode(boolean active) {
		debug = active;
	}

	@Override
	public Integer getNumSamples() {		
		return steps;
	}
}
