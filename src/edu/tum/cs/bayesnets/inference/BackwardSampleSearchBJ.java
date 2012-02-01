package edu.tum.cs.bayesnets.inference;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;
import edu.tum.cs.util.datastruct.Map2Set;
import edu.tum.cs.util.datastruct.Pair;
import edu.tum.cs.util.datastruct.PrioritySet;

/**
 * Backward SampleSearch with backjumping
 * @author jain
 */
public class BackwardSampleSearchBJ extends BackwardSampleSearch {
	
	public BackwardSampleSearchBJ(BeliefNetworkEx bn) throws Exception {
		super(bn);
	}
	
	public static class HighestFirst implements Comparator<Integer> {

		@Override
		public int compare(Integer o1, Integer o2) {
			return -o1.compareTo(o2);
		}
		
	}
	
	@Override
	public void getSample(WeightedSample s) throws Exception {
		Map2Set<BeliefNode,Integer> domExclusions = new Map2Set<BeliefNode,Integer>();
		
		initSample(s);				
		backSamplingDistributionCache = new HashMap<BeliefNode, BackSamplingDistribution>();
		
		boolean backtracking = false;	
		HighestFirst highestFirst = new HighestFirst();
		HashMap<Integer,PrioritySet<Integer>> backtrackQueues = new HashMap<Integer,PrioritySet<Integer>>();
		
		for(int i = 0; i < samplingOrder.size();) {
			currentOrderIndex = i;
			Pair<BeliefNode,NodeMode> p = samplingOrder.get(i);
			
			// get the node 
			BeliefNode node =  p.first;
			NodeMode mode = p.second;

			// if we got to the node backtracking, we add the last value as an exclusion
			if(backtracking) {
				domExclusions.add(node, sampledIndices[i]);
				if(mode == NodeMode.Outside)
					throw new Exception("Backtracked to node outside order");
			}
			else {
				// if we get to a node going forward, forget all exclusions and invalidate cache
				domExclusions.remove(node);				
				if(mode == NodeMode.Backward) backSamplingDistributionCache.remove(node);
				backtrackQueues.remove(i);
			}
			
			// info
			++s.operations;
			//if(s.operations == 10000) debug=true;
			if(debug) 
				out.printf("  Op%d: #%d %s\n", s.operations, i, node.getName());
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
				
			if(valueSuccessfullyAssigned){ // go forward
				// end backtracking 
				backtracking = false;
				
				++i;
				//backtrackQueues.remove(i);
			}
			else { // backtrack				
				if(i == 0) // can't backtrack further
					throw new Exception("Backtracking past first level. Most likely, the evidence that was specified is contradictory");
				
				backtracking = true;
				
				PrioritySet<Integer> backtrackQueue = backtrackQueues.get(i);
				if(backtrackQueue == null)
					backtrackQueue = new PrioritySet<Integer>(new PriorityQueue<Integer>(1, highestFirst));
				if(debug) System.out.printf("    initial backtrack queue: %s\n", backtrackQueue);
				
				// extend the queue depending on the current constraint
				if(mode == NodeMode.Backward) {		
					//if(debug) System.out.println("    parents: " + bsAlreadyGivenParents.get(node));
					// nodes that instantiated parents that were given before
					for(BeliefNode parent : bsAlreadyGivenParents.get(node)) {
						Integer level = node2instantiatorOrderIndex.get(parent);						
						if(level != null) {
							if(debug) System.out.printf("      adding %d (instantiated parent %s)\n", level, parent.getName());
							assert level < i;
							backtrackQueue.add(level);
						}
					}
					// the node that instantiated the node itself
					Integer level = node2instantiatorOrderIndex.get(node);
					if(debug) System.out.println("      adding " + level + " (instantiator of main node)");
					if(level != null)
						backtrackQueue.add(level);
				}
				else { // for forward and outside nodes, add the nodes that instantiated the parents
					BeliefNode[] domprod = node.getCPF().getDomainProduct();
					for(int j = 1; j < domprod.length; j++) {
						Integer level = node2instantiatorOrderIndex.get(domprod[j]);
						if(debug) System.out.printf("      adding %d (instantiated parent %s)\n", level, domprod[j].getName());
						if(level != null)
							backtrackQueue.add(level);
					}
					// for outside nodes also the node that instantiated this node (if any)
					if(mode == NodeMode.Outside) {
						Integer level = node2instantiatorOrderIndex.get(node);
						if(debug) System.out.println("      adding " + level + " (instantiator of main node)");
						if(level != null)
							backtrackQueue.add(level);
					}
				}
				
				// back jump
				Integer iprev = i;
				if(backtrackQueue.isEmpty())
					throw new Exception("Nowhere left to backjump to from node #" + i + ". Most likely, the evidence has 0 probability.");
				else
					i = backtrackQueue.remove();
				
				assert i < iprev : "Invalid backjump from " + iprev + " to " + i;

				// undo all assignments along the way (necessary for backward sampling distributions to be constructed correctly)
				for(int j = iprev-1; j >= i; j--)
					undoAssignment(j, s);
				
				// merge to update the new node's backtracking queue 
				PrioritySet<Integer> oldQueue = backtrackQueues.get(i);
				if(oldQueue == null) {
					//backtrackQueues.put(i, backtrackQueue); // unsafe? -- would assign same queue to i and i-1
					oldQueue = new PrioritySet<Integer>(new PriorityQueue<Integer>(1, highestFirst));
					backtrackQueues.put(i, oldQueue);
				}
				for(Integer j : backtrackQueue)
					oldQueue.add(j);
				
				// this is probably unnecessary
				domExclusions.remove(node);
				if(mode == NodeMode.Backward) backSamplingDistributionCache.remove(node);
				
				if(debug) System.out.printf("    backtracking to #%d, queue: %s\n", i, backtrackQueue.toString());
				s.trials++;
			}				
		}
	}
	
	protected void undoAssignment(int i, WeightedSample s) {
		Pair<BeliefNode, NodeMode> p = samplingOrder.get(i);
		switch(p.second) {
		case Backward:					
			for(Integer idx : assignedNodeIndicesByOrderIndex.get(i)) 
				s.nodeDomainIndices[idx] = -1;
			break;
		case Forward:
			s.nodeDomainIndices[getNodeIndex(p.first)] = -1;
			break;
		}
	}

	protected HashMap<BeliefNode,Integer> node2instantiatorOrderIndex;
	protected HashMap<BeliefNode,Vector<BeliefNode>> bsAlreadyGivenParents;
	protected HashMap<BeliefNode,Integer> node2orderIndex;
	
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
		node2instantiatorOrderIndex = new HashMap<BeliefNode,Integer>();
		bsAlreadyGivenParents = new HashMap<BeliefNode,Vector<BeliefNode>>();
		node2orderIndex = new HashMap<BeliefNode,Integer>();
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
			Integer orderIndex = samplingOrder.size();
			BeliefNode node = backSamplingCandidates.remove();
			// check if there are any uninstantiated parents
			BeliefNode[] domProd = node.getCPF().getDomainProduct();
			boolean doBackSampling = false;
			Vector<BeliefNode> givenParents = new Vector<BeliefNode>();
			for(int j = 1; j < domProd.length; j++) {
				BeliefNode parent = domProd[j];
				// if there are uninstantiated parents, we do backward sampling on the child node
				if(uninstantiatedNodes.remove(parent)) { 
					doBackSampling = true;
					backSamplingCandidates.add(parent);
					node2instantiatorOrderIndex.put(parent, orderIndex);
				}
				else
					givenParents.add(parent);
			}
			if(doBackSampling) {
				backwardSampledNodes.add(node);
				samplingOrder.add(new Pair<BeliefNode,NodeMode>(node, NodeMode.Backward));
				bsAlreadyGivenParents.put(node, givenParents);
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
				node2instantiatorOrderIndex.put(nodes[i], samplingOrder.size());
				samplingOrder.add(new Pair<BeliefNode,NodeMode>(nodes[i], NodeMode.Forward));
			}
		}
		
		Integer i = 0;
		for(Pair<BeliefNode,NodeMode> p : samplingOrder)
			node2orderIndex.put(p.first, i++);
	}
}
