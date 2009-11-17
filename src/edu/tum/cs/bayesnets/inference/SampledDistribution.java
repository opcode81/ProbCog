package edu.tum.cs.bayesnets.inference;

import java.io.PrintStream;
import java.io.Serializable;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.inference.BasicSampledDistribution;

/**
 * class that allows the incremental construction of a probability distribution from (weighted) samples
 * (see {@link WeightedSample})
 * @author jain
 *
 */
public class SampledDistribution extends BasicSampledDistribution implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;
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
		this.Z = 0.0;
		BeliefNode[] nodes = bn.bn.getNodes();
		values = new double[nodes.length][];
		for(int i = 0; i < nodes.length; i++)
			values[i] = new double[nodes[i].getDomain().getOrder()];			
	}
	
	public synchronized void addSample(WeightedSample s) {
		if(s.weight == 0.0) {
			throw new RuntimeException("Zero-weight sample was added to distribution. Precision loss?");
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
	
	@Override
	public void printVariableDistribution(PrintStream out, int index) {
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
}