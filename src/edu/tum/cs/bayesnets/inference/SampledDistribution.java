package edu.tum.cs.bayesnets.inference;

import java.io.PrintStream;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

/**
 * class that allows the incremental construction of a probability distribution from (weighted) samples
 * (see {@link WeightedSample})
 * @author jain
 *
 */
public class SampledDistribution implements Cloneable {
	/**
	 * an array of values representing the distribution, one for each node and each domain element:
	 * values[i][j] is the value for the j-th domain element of the i-th node in the network
	 */
	public double[][] values;
	/**
	 * the normalization constant that applies to each of the distribution values
	 */
	public double Z;
	/**
	 * the belief network for which we are representing a distribution
	 */
	public BeliefNetworkEx bn;
	/**
	 * values that may be used by certain algorithms to store the number of steps involved in creating the distribution
	 */
	public int trials, steps;
	
	public SampledDistribution(BeliefNetworkEx bn) {
		this.bn = bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		values = new double[nodes.length][];
		for(int i = 0; i < nodes.length; i++)
			values[i] = new double[nodes[i].getDomain().getOrder()];			
	}
	
	public synchronized void addSample(WeightedSample s) {
		if(s.weight == 0.0) {
			throw new RuntimeException("Zero-weight sample was added to distribution.");
		}
		Z += s.weight;
		for(int i = 0; i < s.nodeIndices.length; i++) {
			try {
				values[s.nodeIndices[i]][s.nodeDomainIndices[i]] += s.weight;
			}
			catch(ArrayIndexOutOfBoundsException e) {
				BeliefNode[] nodes = bn.bn.getNodes();
				System.err.println("Error: Node " + nodes[s.nodeIndices[i]].getName() + " was not sampled correctly.");
				throw e;
			}
		}
		trials += s.trials;
		steps++;
	}
	
	public void print(PrintStream out) {
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			printNodeDistribution(out, i);
		}
	}
	
	public double getProbability(int nodeIdx, int domainIdx) {
		return values[nodeIdx][domainIdx] / Z;
	}
	
	public void printNodeDistribution(PrintStream out, int index) {
		BeliefNode node = bn.bn.getNodes()[index];
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
}