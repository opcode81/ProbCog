package edu.tum.cs.bayesnets.relational.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.inference.SampledDistribution;

public class InferenceResult {
	public String varName;
	public String[] domainElements;
	public double[] probabilities;
	
	public InferenceResult(SampledDistribution dist, int nodeIdx) {
		BeliefNode node = dist.bn.bn.getNodes()[nodeIdx];
		varName = node.getName();
		Discrete domain = (Discrete)node.getDomain();
		int dim = domain.getOrder();
		domainElements = new String[dim];
		probabilities = new double[dim];
		for(int j = 0; j < dim; j++) {
			domainElements[j] = domain.getName(j);
			probabilities[j] = dist.getProbability(nodeIdx, j); 
		}
	}
	
	public void print() {
		System.err.println(varName + ":");
		for(int i = 0; i < domainElements.length; i++)
			System.err.println(String.format("  %.4f %s", probabilities[i], domainElements[i]));
	}
}
