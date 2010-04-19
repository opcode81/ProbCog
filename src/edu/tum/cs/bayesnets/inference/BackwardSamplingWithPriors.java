package edu.tum.cs.bayesnets.inference;

import java.util.HashMap;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class BackwardSamplingWithPriors extends BackwardSampling {

	public HashMap<BeliefNode, double[]> priors;

	public static class BackSamplingDistribution extends edu.tum.cs.bayesnets.inference.BackwardSampling.BackSamplingDistribution {

		public Vector<Double> parentProbs;
		
		public BackSamplingDistribution(BackwardSamplingWithPriors sampler) {
			super(sampler);			
			parentProbs = new Vector<Double>();
		}
		
		/**
		 * recursively gets a distribution to backward sample from (represented in probs; the corresponding node states stored in states) 
		 * @param i			the node to instantiate next (as an index into the CPF's domain product)
		 * @param addr		the current setting of node indices of the CPF's domain product
		 * @param cpf		the conditional probability function of the node we are backward sampling
		 */
		@Override
		protected void construct(int i, int[] addr, CPF cpf, int[] nodeDomainIndices) {
			BeliefNode[] domProd = cpf.getDomainProduct();
			if(i == addr.length) {
				double child_prob = cpf.getDouble(addr);
				double parent_prob = 1.0;
				for(int j = 1; j < addr.length; j++) {
					double[] parentPrior = ((BackwardSamplingWithPriors)sampler).priors.get(domProd[j]);
					parent_prob *= parentPrior[addr[j]]; 
				} 
				double p = child_prob * parent_prob;
				if(p != 0) {
					addValue(p, addr.clone());
					parentProbs.add(parent_prob);
				}
				return;
			}		
			int nodeIdx = sampler.nodeIndices.get(domProd[i]);
			if(nodeDomainIndices[nodeIdx] >= 0) {
				addr[i] = nodeDomainIndices[nodeIdx];
				construct(i+1, addr, cpf, nodeDomainIndices);
			}
			else {
				Discrete dom = (Discrete)domProd[i].getDomain();		
				for(int j = 0; j < dom.getOrder(); j++) {
					addr[i] = j;
					construct(i+1, addr, cpf, nodeDomainIndices);
				}
			}
		}
		
		@Override
		public void applyWeight(WeightedSample s, int sampledValue) {
			s.weight *= Z / parentProbs.get(sampledValue);
		}
	}
	
	public BackwardSamplingWithPriors(BeliefNetworkEx bn) throws Exception {
		super(bn);
	}
	
	@Override
	protected BackSamplingDistribution getBackSamplingDistribution(BeliefNode node, WeightedSample s) {
		BackSamplingDistribution d = new BackSamplingDistribution(this);
		d.construct(node, s.nodeDomainIndices);
		return d;
	}
	
	@Override
	protected void prepareInference(int[] evidenceDomainIndices) throws Exception {
		super.prepareInference(evidenceDomainIndices);
		System.out.println("computing priors...");
		computePriors(evidenceDomainIndices);
	}
	
	protected void computePriors(int[] evidenceDomainIndices) {
		priors = new HashMap<BeliefNode, double[]>();
		int[] topOrder = bn.getTopologicalOrder();
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
