package edu.tum.cs.bayesnets.inference;

import java.util.HashMap;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.util.Stopwatch;

public class SampleSearch extends Sampler {
	int[] nodeOrder;
	int currentStep;
	
	public SampleSearch(BeliefNetworkEx bn) {
		super(bn);
		// TODO should guarantee for BLNs that formula nodes appear as early as possible
		nodeOrder = bn.getTopologicalOrder();
	}
	
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {
		// sample
		Stopwatch sw = new Stopwatch();
		createDistribution();
		System.out.println("sampling...");
		sw.start();
		WeightedSample s = new WeightedSample(bn);
		for(int i = 1; i <= numSamples; i++) {
			currentStep = i;
			if(i % infoInterval == 0)
				System.out.println("  step " + i);			
			WeightedSample ret = getWeightedSample(s, nodeOrder, evidenceDomainIndices); 
			if(ret != null) {
				addSample(ret);
				
				if(false) { // debugging of weighting
					System.out.print("w=" + ret.weight);
					for(int j = 0; j < evidenceDomainIndices.length; j++)
						if(evidenceDomainIndices[j] == -1) {
							BeliefNode node = nodes[j];							
							System.out.print(" " + node.getName() + "=" + node.getDomain().getName(s.nodeDomainIndices[j]));
						}
					System.out.println();
				}
			}
		}
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/sample, %d samples)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep(), dist.steps));
		return dist;
	}
	
	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
		s.trials = 0;
		s.weight = 1.0;
		s.trials++;
		if(s.trials > this.maxTrials) {
			if(!this.skipFailedSteps)
				throw new Exception("Could not obtain a countable sample in the maximum allowed number of trials (" + maxTrials + ")");
			else
				return null;
		}
		// assign values to the nodes in order
		HashMap<Integer, boolean[]> domExclusions = new HashMap<Integer, boolean[]>();  
		for(int i=0; i < nodeOrder.length;) {
			if(i == -1)
				throw new Exception("It appears that the evidence is constradictory.");
			int nodeIdx = nodeOrder[i];
			int domainIdx = evidenceDomainIndices[nodeIdx];
			// get domain exclusions
			boolean[] excluded = domExclusions.get(nodeIdx);
			if(excluded == null) {
				excluded = new boolean[nodes[nodeIdx].getDomain().getOrder()];
				domExclusions.put(nodeIdx, excluded);
			}
			// debug info
			if(debug) {					
				int numex = 0;
				for(int j=0; j<excluded.length; j++)
					if(excluded[j])
						numex++;
				System.out.printf("    step %d, node %d '%s' (%d/%d exclusions)\n", currentStep, i, nodes[nodeIdx].getName(), numex, excluded.length);
			}
			// for evidence nodes, adjust the weight
			if(domainIdx >= 0) { 
				s.nodeDomainIndices[nodeIdx] = domainIdx;
				double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
				if(prob != 0.0) {
					s.weight *= prob;
					++i;
					continue;
				}
				else {
					if(debug)
						System.out.println("      evidence with probability 0.0; backtracking...");
				}
			} 
			// for non-evidence nodes, do forward sampling
			else {
				domainIdx = sampleForward(nodes[nodeIdx], s.nodeDomainIndices, excluded);
				if(domainIdx >= 0) {
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					++i;
					continue;
				}
				else if(debug)
					System.out.println("      impossible case; backtracking...");
			}
			// if we get here, we need to backtrack to the last non-evidence node
			s.trials++;
			do {
				// kill the current node's exclusions
				domExclusions.remove(nodeIdx); 
				// add the previous node's setting as an exclusion
				--i;
				if(i < 0)
					throw new Exception("Could not find a sample with non-zero probability. Most likely, the evidence specified has 0 probability.");
				nodeIdx = nodeOrder[i];
				boolean[] prevExcl = domExclusions.get(nodeIdx);
				prevExcl[s.nodeDomainIndices[nodeIdx]] = true;
				// proceed with previous node...				
			} while(evidenceDomainIndices[nodeIdx] != -1);
		}
		return s;
	}
	
	/**
	 * samples forward, i.e. samples a value for 'node' given its parents
	 * @param node  the node for which to sample a value
	 * @param nodeDomainIndices  array of domain indices for all nodes in the network; the values for the parents of 'node' must be set already
	 * @return  the index of the domain element of 'node' that is sampled, or -1 if sampling is impossible because all entries in the relevant column are 0
	 * TODO should use loopy bp/ijgp to initialize importance distributions rather than sampling from prior  
	 */
	protected int sampleForward(BeliefNode node, int[] nodeDomainIndices, boolean[] excluded) {
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		// get the addresses of the first two relevant fields and the difference between them
		for(int i = 1; i < addr.length; i++)
			addr[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];		
		addr[0] = 0; // (the first element in the index into the domain of the node we are sampling)
		int realAddr = cpf.addr2realaddr(addr);
		addr[0] = 1;
		int diff = cpf.addr2realaddr(addr) - realAddr; // diff is the address difference between two consecutive entries in the relevant column
		// get probabilities for outcomes
		double[] cpt_entries = new double[domProd[0].getDomain().getOrder()];
		double sum = 0;
		for(int i = 0; i < cpt_entries.length; i++) {
			double value;
			if(excluded[i])
				value = 0.0;
			else
				value = cpf.getDouble(realAddr); 
			cpt_entries[i] = value;
			sum += value;
			realAddr += diff;
		}
		// if the column contains only zeros, it is an impossible case -> cannot sample
		if(sum == 0)
			return -1;
		return sample(cpt_entries, sum, generator);
	}
}
