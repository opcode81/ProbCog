package edu.tum.cs.bayesnets.inference;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;
import edu.tum.cs.tools.Stopwatch;
import edu.tum.cs.tools.StringTool;

/**
 * an implementation of the backward simulation algorithm as described by Robert Fung and Brendan Del Favero 
 * in "Backward Simulation in Bayesian Networks" (UAI 1994)
 * @author jain
 *
 */
public class BackwardSampling extends Sampler {

	Vector<BeliefNode> backwardSampledNodes;
	Vector<BeliefNode> forwardSampledNodes;
	HashSet<BeliefNode> outsideSamplingOrder;
	int[] evidenceDomainIndices;
	protected int currentStep;
	
	protected static class BackSamplingDistribution {
		public Vector<Double> distribution;
		public Vector<int[]> states;
		double Z;
		protected Sampler sampler;
		
		public BackSamplingDistribution(Sampler sampler) {
			Z = 0.0;
			distribution = new Vector<Double>();
			states = new Vector<int[]>();
			this.sampler = sampler;
		}
		
		public void addValue(double p, int[] state) {
			distribution.add(p);
			states.add(state);
			Z += p;
		}
		
		public void applyWeight(WeightedSample s, int sampledValue) {
			s.weight *= Z;
		}
		
		public void construct(BeliefNode node, int[] nodeDomainIndices) {
			CPF cpf = node.getCPF();
			BeliefNode[] domProd = cpf.getDomainProduct();
			int[] addr = new int[domProd.length];
			addr[0] = nodeDomainIndices[sampler.nodeIndices.get(node)];
			construct(1, addr, cpf, nodeDomainIndices);
		}
		
		/**
		 * recursively constructs the distribution to backward sample from  
		 * @param i			the node to instantiate next (as an index into the CPF's domain product)
		 * @param addr		the current setting of node indices of the CPF's domain product
		 * @param cpf		the conditional probability function of the node we are backward sampling
		 * @param d			the distribution to fill
		 */
		protected void construct(int i, int[] addr, CPF cpf, int[] nodeDomainIndices) {
			if(i == addr.length) {
				double p = cpf.getDouble(addr);
				if(p != 0)
					addValue(p, addr.clone());
				return;
			}
			BeliefNode[] domProd = cpf.getDomainProduct();
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
	}
	
	public BackwardSampling(BeliefNetworkEx bn) {
		super(bn);
	}
	
	/**
	 * for ordering belief nodes in descending order of the tier they are in (as indicated by the topological ordering)
	 * @author jain
	 *
	 */
	public static class TierComparator implements Comparator<BeliefNode> {

		TopologicalOrdering topOrder;
		
		public TierComparator(TopologicalOrdering topOrder) {
			this.topOrder = topOrder;
		}
		
		public int compare(BeliefNode o1, BeliefNode o2) {
			return -(topOrder.getTier(o1) - topOrder.getTier(o2));			
		}		
	}
	
	/**
	 * gets the sampling order by filling the members for backward and forward sampled nodes as well as the set of nodes not in the sampling order
	 * @param evidenceDomainIndices
	 * @throws Exception 
	 */
	protected void getOrdering(int[] evidenceDomainIndices) throws Exception {
		BeliefNode[] nodes = bn.bn.getNodes();
		HashSet<BeliefNode> uninstantiatedNodes = new HashSet<BeliefNode>(Arrays.asList(nodes));
		backwardSampledNodes = new Vector<BeliefNode>();
		forwardSampledNodes = new Vector<BeliefNode>();
		outsideSamplingOrder = new HashSet<BeliefNode>();
		TopologicalOrdering topOrder = new TopologicalSort(bn.bn).run(true);
		PriorityQueue<BeliefNode> backSamplingCandidates = new PriorityQueue<BeliefNode>(1, new TierComparator(topOrder));

		// check which nodes have evidence; ones that are are candidates for backward sampling and are instantiated
		for(int i = 0; i < evidenceDomainIndices.length; i++) {
			if(evidenceDomainIndices[i] >= 0) { 
				backSamplingCandidates.add(nodes[i]);
				uninstantiatedNodes.remove(nodes[i]);
			}
		}
		
		// check all backward sampling candidates
		while(!backSamplingCandidates.isEmpty()) {
			BeliefNode node = backSamplingCandidates.remove();
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
		for(int i : topOrder) {
			if(uninstantiatedNodes.contains(nodes[i]))
				forwardSampledNodes.add(nodes[i]);
		}
	}
	
	/**
	 * samples backward from the given node, instantiating its parents
	 * @param node	
	 * @param s		the sample to store the instantiation information in; the weight is also updated with the normalizing constant that is obtained
	 * @return true if sampling succeeded, false otherwise
	 */
	protected boolean sampleBackward(BeliefNode node, WeightedSample s) {		
		//System.out.println("backward sampling from " + node);
		// get the distribution from which to sample 		
		BackSamplingDistribution d = getBackSamplingDistribution(node, s);
		// sample
		int idx = sample(d.distribution, generator);	
		if(idx == -1)
			return false;
		int[] state = d.states.get(idx);
		// apply weight
		d.applyWeight(s, idx);
		if(s.weight == 0.0)
			return false;
		// apply sampled parent setting
		BeliefNode[] domProd = node.getCPF().getDomainProduct();
		for(int i = 1; i < state.length; i++) {
			int nodeIdx = this.nodeIndices.get(domProd[i]);
			s.nodeDomainIndices[nodeIdx] = state[i];
			//System.out.println("  sampled node " + domProd[i]);
		}
		return true;
	}
	
	protected BackSamplingDistribution getBackSamplingDistribution(BeliefNode node, WeightedSample s) {
		BackSamplingDistribution d = new BackSamplingDistribution(this);
		d.construct(node, s.nodeDomainIndices);
		return d;
	}
	
	protected void prepareInference(int[] evidenceDomainIndices) throws Exception {
		this.evidenceDomainIndices = evidenceDomainIndices;
		getOrdering(evidenceDomainIndices);
		if(debug) {
			System.out.println("sampling backward: " + this.backwardSampledNodes);
			System.out.println("sampling forward: " + this.forwardSampledNodes);
			System.out.println("not in order: " + this.outsideSamplingOrder);
		}
	}
	
	@Override
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {		
		Stopwatch sw = new Stopwatch();
		sw.start();
		
		this.prepareInference(evidenceDomainIndices);
		
		this.createDistribution();
		System.out.println("sampling...");
		WeightedSample s = new WeightedSample(this.bn, evidenceDomainIndices.clone(), 1.0, null, 0);
		for(currentStep = 1; currentStep <= this.numSamples; currentStep++) {	
			if(currentStep % infoInterval == 0)
				System.out.println("  step " + currentStep);
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
		int MAX_TRIALS = this.maxTrials;	
loop1:  for(int t = 1; t <= MAX_TRIALS; t++) {
			// init sample
			s.nodeDomainIndices = evidenceDomainIndices.clone();
			s.weight = 1.0;
			// backward sampling
			for(BeliefNode node : backwardSampledNodes) {
				if(!sampleBackward(node, s)) {
					if(debug) System.out.println("!!! backward sampling failed at " + node + " in step " + currentStep);
					continue loop1;
				}				
			}
			//System.out.println("after backward: weight = " + s.weight);
			// forward sampling
			for(BeliefNode node : forwardSampledNodes) {
				if(!sampleForward(node, s)) {
					if(debug) {
						BeliefNode[] domain_product = node.getCPF().getDomainProduct();
						StringBuffer cond = new StringBuffer();
						for(int i = 1; i < domain_product.length; i++) {
							if(i > 1)
								cond.append(", ");
							cond.append(domain_product[i].getName()).append(" = ");
							cond.append(domain_product[i].getDomain().getName(s.nodeDomainIndices[this.getNodeIndex(domain_product[i])]));
						}
						System.out.println("!!! forward sampling failed at " + node + " in step " + currentStep + "; cond: " + cond);
					}
					continue loop1;
				}
			}
			//System.out.println("after forward: weight = " + s.weight);
			// nodes outside the sampling order: adjust weight
			for(BeliefNode node : outsideSamplingOrder) {
				s.weight *= this.getCPTProbability(node, s.nodeDomainIndices);
				if(s.weight == 0.0) {
					// error diagnosis					
					if(debug) System.out.println("!!! weight became zero at unordered node " + node + " in step " + currentStep);
					if(debug && this instanceof BackwardSamplingWithPriors) {
						double[] dist = ((BackwardSamplingWithPriors)this).priors.get(node);
						System.out.println("prior: " + StringTool.join(", ", dist) + " value=" + s.nodeDomainIndices[getNodeIndex(node)]);
						CPF cpf = node.getCPF();
						BeliefNode[] domProd = cpf.getDomainProduct();						
						int[] addr = new int[domProd.length];
						for(int i = 1; i < addr.length; i++)
							addr[i] = s.nodeDomainIndices[getNodeIndex(domProd[i])];
						for(int i = 0; i < dist.length; i++) {
							addr[0] = i;
							dist[i] = cpf.getDouble(addr);
						}
						System.out.println("cpd: " + StringTool.join(", ", dist));
					}
					continue loop1;
				}
			}
			s.trials = t;
			return;
		}
		throw new RuntimeException("Maximum number of trials exceeded.");
	}
	
	protected boolean sampleForward(BeliefNode node, WeightedSample s) {
		int idx = super.sampleForward(node, s.nodeDomainIndices);
		if(idx == -1)
			return false;
		s.nodeDomainIndices[this.nodeIndices.get(node)] = idx;
		return true;
	}
}
