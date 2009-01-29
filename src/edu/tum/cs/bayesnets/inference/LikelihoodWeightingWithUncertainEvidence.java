package edu.tum.cs.bayesnets.inference;

import java.util.Random;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class LikelihoodWeightingWithUncertainEvidence extends LikelihoodWeighting {
	protected final double evidenceProbability = 0.8;
	
	public LikelihoodWeightingWithUncertainEvidence(BeliefNetworkEx bn) {
		super(bn);
	}
	
	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
		Random rand = new Random();
		BeliefNode[] nodes = bn.bn.getNodes();
		s.trials = 0;
		boolean successful = false;
loop:	while(!successful) {
			s.weight = 1.0;
			s.trials++;
			if(s.trials > this.maxTrials) {
				if(!this.skipFailedSteps)
					throw new Exception("Could not obtain a countable sample in the maximum allowed number of trials (" + maxTrials + ")");
				else
					return null;
			}
			// assign values to the nodes in order
			for(int i=0; i < nodeOrder.length; i++) {
				int nodeIdx = nodeOrder[i];
				int domainIdx = evidenceDomainIndices[nodeIdx];
				// for evidence nodes, adjust the weight
				if(domainIdx >= 0) { 
					// stochastically decide whether we use the original evidence or uniformly choose one of the other values
					double choiceProb = evidenceProbability;
					if(rand.nextDouble() > evidenceProbability) {	
						int numOtherChoices = nodes[nodeIdx].getDomain().getOrder()-1;
						if(numOtherChoices > 0) {
							int newDomIdx = rand.nextInt(numOtherChoices);
							if(newDomIdx >= domainIdx)
								newDomIdx++;
							domainIdx = newDomIdx;
							choiceProb = (1-evidenceProbability)/numOtherChoices;
						}
					}
					s.weight *= choiceProb;
					// 
					nodes[nodeIdx].getDomain();
					// get probability of evidence
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
					if(prob == 0.0) {
						if(debug)
							System.out.println("!!! evidence probability was 0 at node " + nodes[nodeIdx] + " in step " + (dist.steps+1));
						continue loop;
					}
					s.weight *= prob;
					
					/*
					// weigh the evidence according to its probability					
					int domSize = nodes[nodeIdx].getDomain().getOrder();
					double wprob = 0;
					for(int j = 0; j < domSize; j++) {
						s.nodeDomainIndices[nodeIdx] = j;
						double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
						if(j == domainIdx) {
							prob *= evidenceProbability;
						}
						else {
							prob = prob * (1-evidenceProbability) / (domSize-1);
						}
						wprob += prob;
					}
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					s.weight *= wprob;*/
				} 
				// for non-evidence nodes, do forward sampling
				else {
					domainIdx = sampleForward(nodes[nodeIdx], s.nodeDomainIndices);
					if(domainIdx < 0) {
						if(debug)
							System.out.println("!!! could not sample forward because of column with only 0s in CPT of " + nodes[nodeIdx].getName() + " in step " + (dist.steps+1));
						bn.removeAllEvidences();
						continue loop;
					}
					s.nodeDomainIndices[nodeIdx] = domainIdx;
				}
			}
			successful = true;
		}
		return s;		
	}
}
