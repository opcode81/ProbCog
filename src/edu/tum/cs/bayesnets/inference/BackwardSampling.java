package edu.tum.cs.bayesnets.inference;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.tools.MutableDouble;
import edu.tum.cs.tools.Stopwatch;

public class BackwardSampling extends Sampler {

	Vector<BeliefNode> backwardSampledNodes;
	Vector<BeliefNode> forwardSampledNodes;
	HashSet<BeliefNode> outsideSamplingOrder;
	int[] evidenceDomainIndices;
	
	public BackwardSampling(BeliefNetworkEx bn) {
		super(bn);
	}
	
	protected void getOrdering(int[] evidenceDomainIndices) {
		BeliefNode[] nodes = bn.bn.getNodes();
		HashSet<BeliefNode> uninstantiatedNodes = new HashSet<BeliefNode>(Arrays.asList(nodes));
		backwardSampledNodes = new Vector<BeliefNode>();
		forwardSampledNodes = new Vector<BeliefNode>();
		outsideSamplingOrder = new HashSet<BeliefNode>();
		LinkedList<BeliefNode> backSamplingCandidates = new LinkedList<BeliefNode>();

		// check which nodes have evidence; ones that are are candidates for backward sampling and are instantiated
		for(int i = 0; i < evidenceDomainIndices.length; i++) {
			if(evidenceDomainIndices[i] >= 0) { 
				backSamplingCandidates.add(nodes[i]);
				uninstantiatedNodes.remove(nodes[i]);
			}
		}
		
		// check all backward sampling candidates
		while(!backSamplingCandidates.isEmpty()) {
			BeliefNode node = backSamplingCandidates.removeFirst();
			// check if there are any uninstantiated parents
			BeliefNode[] domProd = node.getCPF().getDomainProduct();
			boolean doBackSampling = false;
			for(int j = 1; j < domProd.length; j++) {
				BeliefNode parent = domProd[j];
				// if there are uninstantiated parents, we do backward sampling on the child node
				if(uninstantiatedNodes.remove(parent)) { 
					doBackSampling = true;
					backSamplingCandidates.add(parent);
				}					
			}
			if(doBackSampling)
				backwardSampledNodes.add(node);
			// if there are no uninstantiated parents, the node is not backward sampled but is instantiated,
			// i.e. it is not in the sampling order
			else
				outsideSamplingOrder.add(node);
		}
		
		// schedule all uninstantiated node for forward sampling in the topological order
		int[] topOrder = bn.getTopologicalOrder();
		for(int i : topOrder) {
			if(uninstantiatedNodes.contains(nodes[i]))
				forwardSampledNodes.add(nodes[i]);
		}
	}
		
	protected boolean sampleBackward(BeliefNode node, WeightedSample s) {
		//System.out.println("backward sampling from " + node);
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		// get the distribution from which to sample 
		int[] addr = new int[domProd.length];
		addr[0] = s.nodeDomainIndices[this.nodeIndices.get(node)];
		Vector<Double> probs = new Vector<Double>();
		Vector<int[]> states = new Vector<int[]>();
		MutableDouble Z = new MutableDouble(0.0);
		getBackSamplingDistribution(1, addr, cpf, probs, states, Z);
		if(Z.value == 0)
			return false;
		s.weight *= Z.value;
		// sample
		int idx = sample(probs, generator);		
		int[] state = states.get(idx);
		// apply sampled parent setting		
		for(int i = 1; i < addr.length; i++) {
			int nodeIdx = this.nodeIndices.get(domProd[i]);
			s.nodeDomainIndices[nodeIdx] = state[i];
			//System.out.println("  sampled node " + domProd[i]);
		}
		return true;
	}
	
	protected void getBackSamplingDistribution(int i, int[] addr, CPF cpf, Vector<Double> probs, Vector<int[]> states, MutableDouble Z) {
		if(i == addr.length) {
			double p = cpf.getDouble(addr);
			if(p != 0) {
				probs.add(p);
				states.add(addr.clone());
				Z.value += p;
			}
			return;
		}
		BeliefNode[] domProd = cpf.getDomainProduct();
		int nodeIdx = this.nodeIndices.get(domProd[i]);
		if(evidenceDomainIndices[nodeIdx] >= 0) {
			addr[i] = evidenceDomainIndices[nodeIdx];
			getBackSamplingDistribution(i+1, addr, cpf, probs, states, Z);
		}
		else {
			Discrete dom = (Discrete)domProd[i].getDomain();		
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				getBackSamplingDistribution(i+1, addr, cpf, probs, states, Z);
			}
		}
	}

	@Override
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {		
		Stopwatch sw = new Stopwatch();
		sw.start();
		
		this.evidenceDomainIndices = evidenceDomainIndices;
		getOrdering(evidenceDomainIndices);
		if(false) {
			System.out.println("sampling backward: " + this.backwardSampledNodes);
			System.out.println("sampling forward: " + this.forwardSampledNodes);
			System.out.println("not in order: " + this.outsideSamplingOrder);
		}
		
		this.createDistribution();
		WeightedSample s = new WeightedSample(this.bn, evidenceDomainIndices.clone(), 1.0, null, 0);
		for(int i = 1; i <= this.numSamples; i++) {	
			if(i % infoInterval == 0)
				System.out.println("  step " + i);
			getSample(s);
			this.addSample(s);
		}
		
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/step)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep()));
		return this.dist;
	}
	
	/**
	 * gets one full sample of all of the nodes
	 * @param s
	 */
	public void getSample(WeightedSample s) {
		int MAX_TRIALS = 5000;	
		s.weight = 1.0;
loop1:  for(int t = 1; t <= MAX_TRIALS; t++) {
			// backward sampling
			for(BeliefNode node : backwardSampledNodes) {
				if(!sampleBackward(node, s)) {
					System.out.println("!!! backward sampling failed at " + node);
					continue loop1;
				}				
			}
			// forward sampling
			for(BeliefNode node : forwardSampledNodes) {
				if(!sampleForward(node, s)) {
					System.out.println("!!! forward sampling failed at " + node);
					continue loop1;
				}
			}
			// nodes outside the sampling order: adjust weight
			for(BeliefNode node : outsideSamplingOrder) {
				s.weight *= this.getCPTProbability(node, s.nodeDomainIndices);
			}
			s.trials = t;
			return;
		}
		throw new RuntimeException("Maximum number of trials exceeded.");
	}
	
	public boolean sampleForward(BeliefNode node, WeightedSample s) {
		int idx = super.sampleForward(node, s.nodeDomainIndices);
		if(idx == -1)
			return false;
		s.nodeDomainIndices[this.nodeIndices.get(node)] = idx;
		return true;
	}
}
