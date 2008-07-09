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
public class SampledDistribution {
	public double[][] sums;
	public double Z;
	public BeliefNetworkEx bn;
	public int trials, steps;
	
	public SampledDistribution(BeliefNetworkEx bn) {
		this.bn = bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		sums = new double[nodes.length][];
		for(int i = 0; i < nodes.length; i++)
			sums[i] = new double[nodes[i].getDomain().getOrder()];			
	}
	
	public void addSample(WeightedSample s) {
		if(s.weight == 0.0) {
			throw new RuntimeException("Zero-weight sample was added to distribution.");
		}
		Z += s.weight;
		for(int i = 0; i < s.nodeIndices.length; i++) {
			try {
				sums[s.nodeIndices[i]][s.nodeDomainIndices[i]] += s.weight;
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
		for(int i = 0; i < bn.bn.getNodes().length; i++) {
			printNodeDistribution(out, i);
		}
	}
	
	public double getProbability(int nodeIdx, int domainIdx) {
		return sums[nodeIdx][domainIdx] / Z;
	}
	
	public void printNodeDistribution(PrintStream out, int index) {
		BeliefNode node = bn.bn.getNodes()[index];
		out.println(node.getName() + ":");
		Discrete domain = (Discrete)node.getDomain();
		for(int j = 0; j < domain.getOrder(); j++) {
			double prob = sums[index][j] / Z;
			out.println(String.format("  %.4f %s", prob, domain.getName(j)));
		}
	}
	
	public double getTrialsPerStep() {
		return (double)trials/steps;
	}
}