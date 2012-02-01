package edu.tum.cs.bayesnets.inference;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;
import edu.tum.cs.util.datastruct.Map2D;
import edu.tum.cs.util.datastruct.Map2List;
import edu.tum.cs.util.datastruct.Map2Set;
import edu.tum.cs.util.datastruct.Pair;

/**
 * Backward SampleSearch: a combination of backward simulation and sample searching
 * with backtracking. This simple implementation uses chronological backtracking.
 * @author jain
 */
public class BackwardSampleSearch extends BackwardSamplingWithPriors {
	
	protected enum NodeMode {Backward, Forward, Outside};
	
	protected TopologicalOrdering topOrder;
	protected boolean useProperWeighting = false;
	/**
	 * the order in which nodes are sampled along with the mode in which they are to be handled
	 */
	protected Vector<Pair<BeliefNode, NodeMode>> samplingOrder;
	/**
	 * the index in the sampling order of the node currently being treated
	 */
	protected int currentOrderIndex;
	/**
	 * the currently sampled indices that were sampled from the applicable distributions,
	 *  i.e. sampledIndices[i] = j if for the i-th node in the sampling order, we sampled
	 *  the j-th value in the distribution (regardless whether it is a backward sampling 
	 *  distribution or a condition distribution)
	 */
	protected int[] sampledIndices;
	/**
	 * mapping from the sampling order index of a node N to a list of node indices 
	 * that are assigned by backward sampling N
	 */
	protected Map2List<Integer, Integer> assignedNodeIndicesByOrderIndex;
	/**
	 * used only for proper weighting scheme 
	 */	
	protected HashMap<BeliefNode, BackSamplingDistribution> backSamplingDistributionCache;
	protected HashMap<BeliefNode,Double> weightingFactors;
	/**
	 * whether to cache backward sampling distributions
	 */
	protected boolean useCache = true;
	
	public BackwardSampleSearch(BeliefNetworkEx bn) throws Exception {
		super(bn);		
		this.paramHandler.add("unbiased","setUseProperWeighting");
	}
	
	public void setUseProperWeighting(boolean enabled){
		useProperWeighting = enabled;
	}
	
	protected boolean sampleForward(BeliefNode node, WeightedSample s, Set<Integer> excluded) throws Exception {
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		addr[0] = 0;
		for(int i = 1; i < addr.length; i++)
			addr[i] = s.nodeDomainIndices[this.nodeIndices.get(domProd[i])];
		int realAddr = cpf.addr2realaddr(addr); // address of the first element in the distribution we sample from
		int addrOffset = cpf.getColumnValueAddressOffset();
		
		// get probabilities for outcomes
		double[] cpt_entries = new double[domProd[0].getDomain().getOrder()];
		double sum = 0;
		double value;
		for(int i = 0; i < cpt_entries.length; i++) {
			if(excluded != null && excluded.contains(i)) {
				value = 0.0;
				//System.out.println("forward exclusion");
			}
			else
				value = cpf.getDouble(realAddr);
			if(debug) out.printf("      %d: %f\n", i, value);
			cpt_entries[i] = value;
			sum += value;
			realAddr += addrOffset;
		}
		if(sum == 0)
			return false;
		
		// sample
		int domIdx = sample(cpt_entries, sum, generator);
		s.nodeDomainIndices[this.nodeIndices.get(node)] = domIdx;		
		sampledIndices[currentOrderIndex] = domIdx;
		
		// remember weighting factor
		weightingFactors.put(node, getCPTProbability(node, s.nodeDomainIndices) / (cpt_entries[domIdx] / sum));
		
		if(debug) out.println("    assigned " + domIdx);
		return true;
	}
	
	protected boolean sampleBackward(BeliefNode node, WeightedSample s, Set<Integer> excluded) throws Exception{
		
		// get backward sampling distribution
		BackSamplingDistribution d = backSamplingDistributionCache.get(node);
		if(d == null) {
			d = getBackSamplingDistribution(node, s);
			if(useCache) backSamplingDistributionCache.put(node, d);
		}
		
		// get normalisation constant
		double Z = 0.0;
		Integer i = 0;
		int numValues = 0;
		for(Double v : d.distribution) { 
			if(excluded == null || !excluded.contains(i)) {
				if(v > 0) {
					Z += v;				
					numValues++;
				}
			}
			++i;
		}
	
		if(Z == 0.0)
			return false;
		
		if(debug) System.out.printf("      %d choosable values in distribution\n", numValues);
		
		// sample a value
		int idx = sample(d.distribution, Z, excluded, generator);
		int[] state = d.states.get(idx);
		this.sampledIndices[currentOrderIndex] = idx;
		
		// apply sampled parent setting
		boolean buildAssignedIndices = assignedNodeIndicesByOrderIndex.get(currentOrderIndex) == null;
		BeliefNode[] domProd = node.getCPF().getDomainProduct();		
		for(i = 1; i < state.length; i++) {
			int nodeIdx = getNodeIndex(domProd[i]);
			if(buildAssignedIndices && s.nodeDomainIndices[nodeIdx] == -1)
				assignedNodeIndicesByOrderIndex.add(currentOrderIndex, nodeIdx);
			s.nodeDomainIndices[nodeIdx] = state[i];
		}

		// save same data for weighting	
		this.weightingFactors.put(node, Z / d.parentProbs.get(idx));
		
		return true;
	}

	
	/**
	 * sampling from a distribution with exclusions
	 * @param distribution the distribution
	 * @param sum the normalization constant of the distribution (which must consider the exclusions already)
	 * @param excluded the set of excluded distribution indices 
	 * @param generator
	 * @return a distribution index or -1 if no value can be sampled
	 */
	public static int sample(Collection<Double> distribution, double sum, Set<Integer> excluded, Random generator) {
		double random = generator.nextDouble() * sum;
		sum = 0;
		Integer i = 0;
		for(Double d : distribution) {
			if(excluded == null || !excluded.contains(i)) {
				sum += d;
				if(sum >= random)
					return i;
			}
			++i;
		}
		return -1;		
	}
	
	@Override
	protected void _initialize() throws Exception {
		super._initialize();
		sampledIndices = new int[samplingOrder.size()];
		assignedNodeIndicesByOrderIndex = new Map2List<Integer, Integer>();		
		weightingFactors = new HashMap<BeliefNode, Double>();
	}
	
	@Override
	public void getSample(WeightedSample s) throws Exception {
		Map2Set<BeliefNode,Integer> domExclusions = new Map2Set<BeliefNode,Integer>();
		
		initSample(s);				
		backSamplingDistributionCache = new HashMap<BeliefNode, BackSamplingDistribution>();
		
		boolean backtracking = false;		
		
		for(int i = 0; i < samplingOrder.size();) {
			currentOrderIndex = i;
			Pair<BeliefNode,NodeMode> p = samplingOrder.get(i);
			
			// get the node 
			BeliefNode node =  p.first;
			NodeMode mode = p.second;

			// if we got to the node backtracking, we add the last value as an exclusion
			if(backtracking) {
				switch(mode) {
				case Backward:
					domExclusions.add(node, sampledIndices[i]);
					for(Integer idx : assignedNodeIndicesByOrderIndex.get(i)) 
						s.nodeDomainIndices[idx] = -1;
					break;
				case Forward:
					domExclusions.add(node, sampledIndices[i]);
					s.nodeDomainIndices[getNodeIndex(node)] = -1;
					break;
				case Outside:
					--i;
					continue;
				}
				backtracking = false;
			}
			
			// info
			if(debug) 
				out.printf("  Op%d: #%d %s\n", ++s.operations, i, node.getName());
			else 
				if(infoInterval == 1) out.printf("#%d \r", i);

			// get domain exclusions
			Set<Integer> excluded = domExclusions.get(node);
		
			boolean valueSuccessfullyAssigned = true;
			switch(mode) {
			case Backward:
				if(debug) out.printf("    backward sampling (%d exclusions)\n", excluded == null ? 0 : excluded.size());
				//Stopwatch sw3 = new Stopwatch();
				//sw3.start();
				if(!sampleBackward(node, s, excluded)){
					//if (debug) out.println("CPT contains only zeros for backward sampled node: "+ node);
					valueSuccessfullyAssigned = false;
				}
				break;
			case Forward:			
				if(debug) out.printf("    forward sampling (%d exclusions)\n", excluded == null ? 0 : excluded.size());
				if(!sampleForward(node, s, excluded)){
					//if (debug) out.println("CPT contains only zeros for forward sampled node: "+ node);
					valueSuccessfullyAssigned = false;
				}
				break;
			case Outside:
				if(debug) out.printf("    outside sampling order\n", excluded == null ? 0 : excluded.size());
				double prob = this.getCPTProbability(node, s.nodeDomainIndices);
				if(prob == 0.0)
					valueSuccessfullyAssigned = false;
				break;
			}
			
				//out.println("Node "+ node+ "has prob.: "+ getCPTProbability(node,s.nodeDomainIndices));
				//check if the sample is consistent
				//if (getCPTProbability(node,s.nodeDomainIndices)==0.0){
				
			if(!valueSuccessfullyAssigned){
				// backtrack
				if(debug) System.out.println("    backtracking");
				domExclusions.remove(node);
				if(mode == NodeMode.Backward) backSamplingDistributionCache.remove(node);
				backtracking = true;
				--i;
				if(i < 0)
					throw new Exception("Evidence seems to be contradictory");
				s.trials++;
			}				
			else {
				// go forward
				++i;
			}
		}
	}

	
	/**
	 * gets the sampling order by filling the members for backward and forward sampled nodes as well as the set of nodes not in the sampling order
	 * @param evidenceDomainIndices
	 * @throws Exception 
	 */
	@Override
	protected void getOrdering(int[] evidenceDomainIndices) throws Exception {
		HashSet<BeliefNode> uninstantiatedNodes = new HashSet<BeliefNode>(Arrays.asList(nodes));
		backwardSampledNodes = new Vector<BeliefNode>();
		forwardSampledNodes = new Vector<BeliefNode>();
		outsideSamplingOrder = new HashSet<BeliefNode>();
		samplingOrder = new Vector<Pair<BeliefNode,NodeMode>>();
		topOrder = new TopologicalSort(bn.bn).run(true);
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
			if(doBackSampling) {
				backwardSampledNodes.add(node);
				samplingOrder.add(new Pair<BeliefNode,NodeMode>(node, NodeMode.Backward));
			}
			// if there are no uninstantiated parents, the node is not backward sampled but is instantiated,
			// i.e. it is not in the sampling order
			else {
				outsideSamplingOrder.add(node);
				samplingOrder.add(new Pair<BeliefNode,NodeMode>(node, NodeMode.Outside));
			}
		}
		
		// schedule all uninstantiated node for forward sampling in the topological order
		for(int i : topOrder) {
			if(uninstantiatedNodes.contains(nodes[i])) {
				forwardSampledNodes.add(nodes[i]);
				samplingOrder.add(new Pair<BeliefNode,NodeMode>(nodes[i], NodeMode.Forward));
			}
		}
	}
	
	public IDistributionBuilder createDistributionBuilder() throws Exception {		
		return new DistributionBuilder();
	}

	protected class DistributionBuilder implements IDistributionBuilder {

		protected Map2D<Integer,BigInteger,Double> minFactors;
		protected Vector<Pair<WeightedSample,Vector<Pair<Integer,BigInteger>>>> samples;
		protected SampledDistribution dist;
		protected boolean dirty = false;
		
		public DistributionBuilder() throws Exception {
			if(useProperWeighting) {
				minFactors = new Map2D<Integer,BigInteger,Double>();
				samples = new Vector<Pair<WeightedSample,Vector<Pair<Integer,BigInteger>>>>();
			}
			else
				dist = createDistribution();
		}
		
		@Override
		public synchronized void addSample(WeightedSample s) throws Exception {
			dirty = true;
			s.weight = 1.0;

			// for both weighting schemes:
			// * the nodes that are outside the
			//   sampling order are sampled with probability 1, therefore
			//   the conditional probability of those nodes applies as a factor
			for(BeliefNode node : outsideSamplingOrder) {
				double p = getCPTProbability(node, s.nodeDomainIndices);
				s.weight *= p;
				if(s.weight == 0.0) throw new Exception(p != 0.0 ? "Precision loss while computing sample weight" : "Sample has 0 probability");
			}
			
			if(!useProperWeighting) {				
				// simple weighting scheme (ignores the fact that we actually sample 
				// from the backtrack-free distribution):
				// just use the factors that we recorded
				
				// * forward sampled nodes are sampled according to the prior,
				//   therefore no factor would usually apply.
				//   However, if CPTs are allowed to contain 0 columns, 
				//   then the sampling probability may be higher as a result of backtracking. 
				for(BeliefNode node : forwardSampledNodes) {
					s.weight *= weightingFactors.get(node);
					if(s.weight == 0.0) throw new Exception("Precision loss while computing sample weight");
				}

				for(BeliefNode node : backwardSampledNodes) {
					s.weight *= weightingFactors.get(node);
					if(s.weight == 0.0) { throw new Exception("Precision loss while computing sample weight");}
				}		
				
				// and we just add the sample to the distribution 
				dist.addSample(s);
			}
			else {				
				// unbiased weighting: store all samples and keep track of minimum factors
				
				BigInteger partAssign = BigInteger.valueOf(0);
				Vector<Pair<Integer,BigInteger>> keys = new Vector<Pair<Integer,BigInteger>>();
				for(int i = 0; i < samplingOrder.size(); i++) {
					Pair<BeliefNode,NodeMode> p = samplingOrder.get(i);
					if(p.second == NodeMode.Outside) {
						continue;
					}
					
					// extend assignment
					BigInteger distSize;
					if(p.second == NodeMode.Backward)
						distSize = BigInteger.valueOf(p.first.getCPF().getRowLength()); // the row length is an upper bound for the distribution size
					else
						distSize = BigInteger.valueOf(p.first.getDomain().getOrder());
					partAssign = partAssign.multiply(distSize);
					partAssign = partAssign.add(BigInteger.valueOf(sampledIndices[i]));
					
					Double prevFactor = minFactors.get(i,partAssign);
					Double factor = weightingFactors.get(p.first);
					if(prevFactor == null || factor < prevFactor) { // TODO verify this condition
						minFactors.put(i, partAssign, factor);
						/*if(prevFactor != null)
							System.out.println("reducing factor " + prevFactor + " to " + factor);
						*/
					}
					
					keys.add(new Pair<Integer,BigInteger>(i,partAssign));
				}
				
				Pair<WeightedSample, Vector<Pair<Integer,BigInteger>>> sample = new Pair<WeightedSample, Vector<Pair<Integer,BigInteger>>>(s.clone(), keys);
				samples.add(sample);
			}
		}

		@Override
		public synchronized SampledDistribution getDistribution() throws Exception {
			if(!useProperWeighting)
				return dist;
			else {
				System.out.println("unbiased sample weighting...");
				if(!dirty)
					return dist;
				dist = createDistribution();
				for(Pair<WeightedSample, Vector<Pair<Integer,BigInteger>>> sample : samples) {
					WeightedSample s = sample.first;
					
					s.weight = 1.0;
					for(BeliefNode node : outsideSamplingOrder) {
						double p = getCPTProbability(node, s.nodeDomainIndices);
						s.weight *= p;
						if(s.weight == 0.0) throw new Exception(p != 0.0 ? "Precision loss while computing sample weight" : "Sample has 0 probability");
					}
					
					for(Pair<Integer,BigInteger> key : sample.second) {
						Double factor = minFactors.get(key.first, key.second);
						//System.out.println("factor " + factor);
						s.weight *= factor;
						if(s.weight == 0.0) { throw new Exception("Precision loss while computing sample weight");}
					}
					//System.out.println("added sample with weight " + s.weight);
					dist.addSample(s);
				}
				dirty = false;
				return dist;
			}
		}
		
	}
}
