package probcog.srl.directed.inference;

import java.util.HashMap;
import java.util.HashSet;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.inference.BackwardSamplingWithPriors;
import probcog.bayesnets.inference.SampledDistribution;
import probcog.bayesnets.inference.WeightedSample;
import probcog.srl.directed.bln.GroundBLN;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.datastruct.Cache2D;
import edu.tum.cs.util.datastruct.MutableDouble;

public class LiftedBackwardSampling extends Sampler {

	/**
	 * a mapping from belief node objects to integers identifying equivalence classes with respect to the algorithm
	 */
	HashMap<BeliefNode,Integer> node2class = new HashMap<BeliefNode, Integer>();
	
	public LiftedBackwardSampling(GroundBLN gbln) throws Exception {
		super(gbln);
	}
	
	@Override
	public SampledDistribution _infer() throws Exception {
		
		// compute node equivalence classes with respect to the "backward sampling
		// with children" procedure		
		System.out.println("computing equivalence classes...");
		Integer classNo = 0;
		Cache2D<String, String, Integer> classes = new Cache2D<String, String, Integer>();
		BeliefNetworkEx groundBN = gbln.getGroundNetwork();		
		for(BeliefNode node : groundBN.bn.getNodes()) {
			// construct string key
			StringBuffer key = new StringBuffer();
			BeliefNode[] domprod = node.getCPF().getDomainProduct();
			for(int i = 1; i < domprod.length; i++) {
				key.append(",").append(gbln.getCPFID(domprod[i]));
				for(BeliefNode c : groundBN.bn.getChildren(domprod[i])) {					
					for(BeliefNode d : c.getCPF().getDomainProduct()) {
						key.append(",").append(gbln.getCPFID(d));
					}
				}				
			}			
			String skey = key.toString();
			// check if we already have it
			String mainCPFID = gbln.getCPFID(node);
			if(mainCPFID == null)
				throw new Exception("Node " + node + " has no CPF-ID");
			Integer value = classes.get(mainCPFID, skey);
			if(value == null) {
				value = ++classNo;
				classes.put(classNo);
			}
			node2class.put(node, value);
			if(debug) 
				System.out.println(node + " is class " + value + "\n  " + mainCPFID + skey);
		}
		System.out.println("  reduced " + groundBN.bn.getNodes().length + " nodes to " + classNo + " equivalence classes");
			
		// inference
		String[][] evidence = this.gbln.getDatabase().getEntriesAsArray();
		int[] evidenceDomainIndices = gbln.getFullEvidence(evidence);
		Sampler sampler = new Sampler(gbln.getGroundNetwork());
		sampler.setDebugMode(debug);
		sampler.setNumSamples(numSamples);
		sampler.setInfoInterval(infoInterval);
		sampler.setEvidence(evidenceDomainIndices);
		//sampler.setMaxTrials(maxTrials);
		//sampler.setSkipFailedSteps(skipFailedSteps);
		SampledDistribution dist = sampler.infer();
		
		return dist;
	}
	
	/**
	 * the actual backward sampler (largely equivalent to BackwardSamplingWithChildren)
	 * @author jain
	 *
	 */
	protected class Sampler extends BackwardSamplingWithPriors {

		// TODO I think there are problems with the probability caches here, because using the CPFID alone ignores the fact that the priors may be different since nodes with the same CPFID may have differently instantiated ancestors
		// Could solve this problem by using a 3D cache that includes the prior of the node
		
		protected Cache2D<String, Integer, Double> probCache;
		/**
		 * cache of backward sampling distributions
		 */
		protected Cache2D<Integer, Long, BackSamplingDistribution> distCache;
		protected Stopwatch probSW, distSW;
		protected boolean useDistributionCache = true;
		protected boolean useProbabilityCache = false;
		
		public class BackSamplingDistribution extends probcog.bayesnets.inference.BackwardSamplingWithPriors.BackSamplingDistribution {
			
			public BackSamplingDistribution(BackwardSamplingWithPriors sampler) {
				super(sampler);			
			}
			
			/**
			 * recursively gets a distribution to backward sample from 
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
					boolean[] tempEvidence = new boolean[addr.length];
					for(int k = 1; k < addr.length; k++) {					
						int nodeIdx = sampler.nodeIndices.get(domProd[k]);
						tempEvidence[k] = nodeDomainIndices[nodeIdx] == -1;
						if(tempEvidence[k])						
							nodeDomainIndices[nodeIdx] = addr[k];
					}
					// consider parent configuration
					double parent_prob = 1.0;
					HashSet<BeliefNode> handledChildren = new HashSet<BeliefNode>();
					handledChildren.add(domProd[0]);
					for(int j = 1; j < addr.length; j++) {
						double[] parentPrior = ((BackwardSamplingWithPriors)sampler).priors.get(domProd[j]);
						parent_prob *= parentPrior[addr[j]]; 
						// consider children of parents with evidence					
						// get child probability
						BeliefNode[] children = sampler.bn.bn.getChildren(domProd[j]);
						for(BeliefNode child : children) {
							if(nodeDomainIndices[sampler.getNodeIndex(child)] >= 0 && !handledChildren.contains(child)) {
								//getProb(childCPF, 0, new int[childCPF.getDomainProduct().length], nodeDomainIndices, p);
								double p = getProb(child, nodeDomainIndices);
								parent_prob *= p;
								handledChildren.add(child);
							}
						}
					}
					// unset temporary evidence
					for(int k = 1; k < addr.length; k++) {
						if(tempEvidence[k])
							nodeDomainIndices[sampler.nodeIndices.get(domProd[k])] = -1;
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
			
			protected double getProb(BeliefNode node, int[] nodeDomainIndices) {
				CPF cpf = node.getCPF();
				boolean debugCache = debug;
				probSW.start();
				// get the key in the CPF-specific cache
				Double cacheValue = null;
				BeliefNode[] domProd = cpf.getDomainProduct();
				int[] addr = new int[domProd.length];	
				boolean allSet = true;
				int key = 0;
				for(int i = 0; i < addr.length; i++) {
					int idx = nodeDomainIndices[sampler.getNodeIndex(domProd[i])];
					allSet = allSet && idx >= 0; 
					addr[i] = idx;
					key *= cpf._SizeBuffer[i]+1;
					key += idx == -1 ? cpf._SizeBuffer[i] : idx;
				}
				if(allSet) {
					probSW.stop();
					return cpf.getDouble(addr);
				}
				// check if we already have the value in the cache
				Double value = null;
				if(useProbabilityCache)
					value = cacheValue = probCache.get(gbln.getCPFID(node), key);
				if(value != null) {
					probSW.stop();
					if(!debugCache)
						return value;					
				}
				// (not in the cache, so) calculate the value
				MutableDouble p = new MutableDouble(0.0);
				getProb(cpf, 0, addr, nodeDomainIndices, p);
				// store in cache
				if(useProbabilityCache) {
					probCache.put(p.value);
					if(cacheValue != null && p.value != cacheValue) {
						throw new RuntimeException("Probability cache mismatch");
					}
			    }
				// return value
				probSW.stop();
				return p.value;
			}
			
			/**
			 * gets the probability indicated by the given CPF for the given domain indices, summing over all parents whose values are not set (i.e. set to -1) in nodeDomainIndices;
			 * i.e. computes the probability of the node whose CPF is provided given the evidence set in nodeDomainIndices
			 * @param cpf					the conditional probability function
			 * @param i						index of the next node to instantiate
			 * @param addr					the address (list of node domain indices relevant to the CPF)
			 * @param nodeDomainIndices		evidences (mapping of all nodes in the network to domain indices, -1 for no evidence)
			 * @param ret					variable in which to store the result (initialize to 0.0, because we are summing probability values)
			 */
			protected void getProb(CPF cpf, int i, int[] addr, int[] nodeDomainIndices, MutableDouble ret) {
				BeliefNode[] domProd = cpf.getDomainProduct();
				// if all nodes have been instantiated...
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
				// otherwise instantiate the next node
				BeliefNode node = domProd[i];
				int nodeIdx = sampler.getNodeIndex(node);
				// - if we have evidence, use it
				if(nodeDomainIndices[nodeIdx] >= 0) {				
					addr[i] = nodeDomainIndices[nodeIdx];
					getProb(cpf, i+1, addr, nodeDomainIndices, ret);
				}
				// - otherwise sum over all settings
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
			BackSamplingDistribution d;
			long key = 0;			
			distSW.start();
			
			if(useDistributionCache) { 
				// calculate key		
				BeliefNode[] domProd = node.getCPF().getDomainProduct();
				// - consider node itself and all parents			
				for(int i = 0; i < domProd.length; i++) {
					BeliefNode n = domProd[i];
					int idx = s.nodeDomainIndices[getNodeIndex(n)];
					int order = n.getDomain().getOrder();
					key *= order + 1;
					key += idx == -1 ? order : idx;
					// - children of parents
					if(i != 0) {
						BeliefNode[] children = bn.bn.getChildren(n);
						for(int j = 0; j < children.length; j++) {
							if(children[j] != node) {
								n = children[j];
								idx = s.nodeDomainIndices[getNodeIndex(n)];
								order = n.getDomain().getOrder();
								key *= order + 1;
								key += idx == -1 ? order : idx;
								// - parents of children						
								BeliefNode[] parentsofchildren = children[j].getCPF().getDomainProduct();
								for(int k = 1; k < parentsofchildren.length; k++) {
									n = parentsofchildren[k];
									idx = s.nodeDomainIndices[getNodeIndex(n)];
									order = n.getDomain().getOrder();
									key *= order + 1;
									key += idx == -1 ? order : idx;
								}
							}
						}
					}
				}
			
				// check if we have a cache value
				d = distCache.get(node2class.get(node), key);
				if(d != null)
					return d;
			}
			
			// obtain new distribution
			d = new BackSamplingDistribution(this);
			d.construct(node, s.nodeDomainIndices);
			
			// store in cache
			if(useDistributionCache)
				distCache.put(d); 
			
			distSW.stop();
			return d;
		}
		
		public Sampler(BeliefNetworkEx bn) throws Exception {
			super(bn);
		}
		
		@Override
		public void _initialize() throws Exception {
			probCache = new Cache2D<String, Integer, Double>();
			distCache = new Cache2D<Integer, Long, BackSamplingDistribution>();
			super._initialize();
		}
		
		@Override
		public void _infer() throws Exception {
			probSW = new Stopwatch();
			distSW = new Stopwatch();
			super._infer();
			System.out.println("prob time: " + probSW.getElapsedTimeSecs());
			System.out.println(String.format("  cache hit ratio: %f (%d accesses)", this.probCache.getHitRatio(), this.probCache.getNumAccesses()));
			System.out.println("dist time: " + distSW.getElapsedTimeSecs());
			System.out.println(String.format("  cache hit ratio: %f (%d accesses)", this.distCache.getHitRatio(), this.distCache.getNumAccesses()));
			System.out.println();
		}
	}
}
