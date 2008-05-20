package edu.tum.cs.bayesnets.inference;

import java.util.HashMap;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.tools.MutableDouble;

public class BackwardSamplingWithPriors extends BackwardSampling {

	HashMap<BeliefNode, double[]> priors;
	
	public BackwardSamplingWithPriors(BeliefNetworkEx bn) {
		super(bn);
	}
	
	/**
	 * recursively gets a distribution to backward sample from (represented in probs; the corresponding node states stored in states) 
	 * @param i			the node to instantiate next (as an index into the CPF's domain product)
	 * @param addr		the current setting of node indices of the CPF's domain product
	 * @param cpf		the conditional probability function of the node we are backward sampling
	 * @param probs		(out) to be filled with probability values of (a subset of) the row of interested
	 * @param states	(out) the states (array of domain indices) corresponding to each of the probability values in probs
	 * @param Z			(out) the normalizing constant (sum of values in probs)
	 */
	@Override
	protected void getBackSamplingDistribution(int i, int[] addr, CPF cpf, Vector<Double> probs, Vector<int[]> states, MutableDouble Z, int[] nodeDomainIndices) {
		BeliefNode[] domProd = cpf.getDomainProduct();
		if(i == addr.length) {
			double child_prob = cpf.getDouble(addr);
			double parent_prob = 1.0;
			for(int j = 1; j < addr.length; j++) {
				double[] parentPrior = priors.get(domProd[j]);
				parent_prob *= parentPrior[addr[j]]; 
			} 
			double p = child_prob * parent_prob;
			if(p != 0) {
				probs.add(p);
				states.add(addr.clone());
				Z.value += p;
			}
			return;
		}		
		int nodeIdx = this.nodeIndices.get(domProd[i]);
		if(nodeDomainIndices[nodeIdx] >= 0) {
			addr[i] = nodeDomainIndices[nodeIdx];
			getBackSamplingDistribution(i+1, addr, cpf, probs, states, Z, nodeDomainIndices);
		}
		else {
			Discrete dom = (Discrete)domProd[i].getDomain();		
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				getBackSamplingDistribution(i+1, addr, cpf, probs, states, Z, nodeDomainIndices);
			}
		}
	}
	
	@Override
	protected void applyBackSamplingWeight(BeliefNode node, WeightedSample s, double Z, int[] addr) {
		// TODO this is inefficient and could be cached during getbacksamplingdist
		BeliefNode[] domProd = node.getCPF().getDomainProduct();
		double parent_prob = 1.0;
		for(int j = 1; j < addr.length; j++) {
			double[] parentPrior = priors.get(domProd[j]);
			parent_prob *= parentPrior[addr[j]]; 
		}
		s.weight *= Z / parent_prob;
	}
	
	@Override
	protected void prepareInference(int[] evidenceDomainIndices) {
		super.prepareInference(evidenceDomainIndices);
		System.out.println("computing priors...");
		computePriors(evidenceDomainIndices);
	}
	
	protected void computePriors(int[] evidenceDomainIndices) {
		priors = new HashMap<BeliefNode, double[]>();
		int[] topOrder = bn.getTopologicalOrder();
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i : topOrder) {
			BeliefNode node = nodes[i];
			double[] dist = new double[node.getDomain().getOrder()];
			int evidence = evidenceDomainIndices[i];
			if(evidence >= 0) {
				for(int j = 0; j < dist.length; j++)
					dist[j] = evidence == j ? 1.0 : 0.0;
			}
			else {
				CPF cpf = node.getCPF();
				computePrior(cpf, 0, new int[cpf.getDomainProduct().length], dist);
			}
			priors.put(node, dist);
		}
	}
	
	protected void computePrior(CPF cpf, int i, int[] addr, double[] dist) {
		BeliefNode[] domProd = cpf.getDomainProduct(); 
		if(i == addr.length) {
			double p = cpf.getDouble(addr); // p = P(node setting | parent configuration)
			for(int j = 1; j < addr.length; j++) {
				double[] parentPrior = priors.get(domProd[j]);
				p *= parentPrior[addr[j]]; 
			} // p = P(node setting, parent configuration)
			dist[addr[0]] += p;
			return;
		}
		BeliefNode node = domProd[i];
		int nodeIdx = getNodeIndex(node);
		if(evidenceDomainIndices[nodeIdx] >= 0) {
			addr[i] = evidenceDomainIndices[nodeIdx];
			computePrior(cpf, i+1, addr, dist);
		}
		else {
			Domain dom = node.getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				computePrior(cpf, i+1, addr, dist);
			}
		}
	}
}
