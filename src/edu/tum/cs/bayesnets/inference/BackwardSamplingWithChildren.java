package edu.tum.cs.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.tools.MutableDouble;

public class BackwardSamplingWithChildren extends BackwardSamplingWithPriors {

	public static class BackSamplingDistribution extends edu.tum.cs.bayesnets.inference.BackwardSamplingWithPriors.BackSamplingDistribution {

		public BackSamplingDistribution(BackwardSamplingWithPriors sampler) {
			super(sampler);			
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
				// temporarily set evidence
				for(int k = 1; k < addr.length; k++) {
					int nodeIdx = sampler.nodeIndices.get(domProd[k]);
					nodeDomainIndices[nodeIdx] = addr[k];
				}
				// consider parent configuration
				double parent_prob = 1.0;
				for(int j = 1; j < addr.length; j++) {
					double[] parentPrior = ((BackwardSamplingWithPriors)sampler).priors.get(domProd[j]);
					parent_prob *= parentPrior[addr[j]]; 
					// consider children of parents with evidence					
					// get child probability
					BeliefNode[] children = sampler.bn.bn.getChildren(domProd[j]);
					for(BeliefNode child : children) {
						if(child != domProd[0] && nodeDomainIndices[sampler.getNodeIndex(child)] >= 0) {
							CPF childCPF = child.getCPF();
							MutableDouble p = new MutableDouble(0.0);
							getProb(childCPF, 0, new int[childCPF.getDomainProduct().length], nodeDomainIndices, p);
							parent_prob *= p.value;
						}
					}
				}
				// unset temporary evidence
				for(int k = 1; k < addr.length; k++) {
					int nodeIdx = sampler.nodeIndices.get(domProd[k]);
					nodeDomainIndices[nodeIdx] = -1;
				}
				// add to distribution
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
		
		protected void getProb(CPF cpf, int i, int[] addr, int[] nodeDomainIndices, MutableDouble ret) {
			BeliefNode[] domProd = cpf.getDomainProduct(); 
			if(i == addr.length) {
				double p = cpf.getDouble(addr); 
				for(int j = 1; j < addr.length; j++) {
					if(nodeDomainIndices[sampler.getNodeIndex(domProd[j])] == -1); {
						double[] parentPrior = ((BackwardSamplingWithPriors)sampler).priors.get(domProd[j]);
						p *= parentPrior[addr[j]];						
					}
				}
				ret.value += p;
				return;
			}
			BeliefNode node = domProd[i];
			int nodeIdx = sampler.getNodeIndex(node);
			if(nodeDomainIndices[nodeIdx] >= 0) {
				addr[i] = nodeDomainIndices[nodeIdx];
				getProb(cpf, i+1, addr, nodeDomainIndices, ret);
			}
			else {
				Domain dom = node.getDomain();
				for(int j = 0; j < dom.getOrder(); j++) {
					addr[i] = j;
					getProb(cpf, i+1, addr, nodeDomainIndices, ret);
				}
			}
		}
	}
	
	@Override
	protected BackSamplingDistribution getBackSamplingDistribution(BeliefNode node, WeightedSample s) {
		BackSamplingDistribution d = new BackSamplingDistribution(this);
		d.construct(node, s.nodeDomainIndices);
		return d;
	}
	
	public BackwardSamplingWithChildren(BeliefNetworkEx bn) {
		super(bn);
	}
}
