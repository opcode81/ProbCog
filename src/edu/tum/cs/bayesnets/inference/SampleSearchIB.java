package edu.tum.cs.bayesnets.inference;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;

/**
 * SampleSearch with "intelligent" backtracking
 * @author jain
 */
public class SampleSearchIB extends SampleSearch {
	HashMap<BeliefNode, Integer> node2orderIndex;
	TopologicalOrdering topologicalOrdering;
	
	public SampleSearchIB(BeliefNetworkEx bn) throws Exception {
		super(bn);			
	}

	@Override
	protected int[] computeNodeOrdering() throws Exception {
		int[] order;
		topologicalOrdering = new TopologicalSort(this.bn.bn).run();
		/*order = new int[nodes.length];
		int i = 0;
		for(int nodeIdx : topologicalOrdering)
			order[i++] = nodeIdx;
		*/			
		order = bn.getTopologicalOrder(); // TODO: Find out why this ordering is so much better
		
		// maintain mapping of node to index in ordering
		node2orderIndex = new HashMap<BeliefNode, Integer>();
		for(int i = 0; i < order.length; i++) 
			node2orderIndex.put(nodes[order[i]], i);			
		
		return order;
	}
	
	protected class SamplingQueue {
		protected PriorityQueue<BeliefNode> queue = new PriorityQueue<BeliefNode>(nodes.length, new SamplingOrderComparator());
		protected HashSet<Integer> enqueued = new HashSet<Integer>();

		public SamplingQueue() {
			// initially add all nodes without parents to the queue
			for(int i : topologicalOrdering.getTier(0)) {
				//System.out.println(nodes[i].getName());
				queue.add(nodes[i]);
				enqueued.add(i);
			}
		}
		
		public boolean add(Integer nodeIdx) {
			if(!enqueued.contains(nodeIdx)) {
				queue.add(nodes[nodeIdx]);
				enqueued.add(nodeIdx);
				return true;
			}
			return false;
		}
		
		public boolean isEmpty() {
			return queue.isEmpty();
		}
		
		public Integer remove() {
			Integer ret = getNodeIndex(queue.remove());
			enqueued.remove(ret);
			return ret;
		}
		
		public class SamplingOrderComparator implements Comparator<BeliefNode> {

			@Override
			public int compare(BeliefNode o1, BeliefNode o2) {					
				return node2orderIndex.get(o1) - node2orderIndex.get(o2);
			}
			
		}
	}
	
	protected class BacktrackOrderingComparator implements Comparator<BeliefNode> {
		
		@Override
		public int compare(BeliefNode o1, BeliefNode o2) {			
			return -(node2orderIndex.get(o1) - node2orderIndex.get(o2));
		}
		
	}
	
	protected class DomainExclusions {
		HashMap<Integer, boolean[]> domExclusions = new HashMap<Integer, boolean[]>();
		
		public boolean[] get(Integer nodeIdx) {
			boolean[] excluded = domExclusions.get(nodeIdx);
			if(excluded == null) {
				excluded = new boolean[nodes[nodeIdx].getDomain().getOrder()];
				domExclusions.put(nodeIdx, excluded);
			}
			return excluded;
		}
		
		public void add(Integer nodeIdx, int domIdx) {
			boolean[] excl = get(nodeIdx);
			excl[domIdx] = true;
		}
		
		public void remove(Integer nodeIdx) {
			domExclusions.remove(nodeIdx);
		}
	}

	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
		s.trials = 0;
		s.operations = 0;
		s.weight = 1.0;
		s.trials++;		

		SamplingQueue queue = new SamplingQueue();		
		PriorityQueue<BeliefNode> backtrack = new PriorityQueue<BeliefNode>(10, new BacktrackOrderingComparator());
		boolean backtracking = false;
		DomainExclusions domExclusions = new DomainExclusions();

		// assign values to the nodes in order
		double[] samplingProb = new double[nodeOrder.length];
		while(!queue.isEmpty() || backtracking) {
			s.operations++;
			int nodeIdx;
			boolean valueSuccessfullyAssigned = false;
			
			if(!backtracking) {
				nodeIdx = queue.remove();
			}
			else {
				BeliefNode node = backtrack.peek();
				nodeIdx = getNodeIndex(node);
				if(debug) System.out.println("    backtracking to #" + node2orderIndex.get(nodes[nodeIdx]) + " " + nodes[nodeIdx].getName() + ", current setting: " + s.nodeDomainIndices[nodeIdx] + ", b-queue size: " + backtrack.size());
				
				// since we are backtracking, the previous setting of this node is 
				// inapplicable, so we add it to the exclusions
				domExclusions.add(nodeIdx, s.nodeDomainIndices[nodeIdx]);
			}

			// enqueue all children of the node
			for(BeliefNode child : bn.bn.getChildren(nodes[nodeIdx])) {
				if(queue.add(getNodeIndex(child)))
					if(debug) System.out.println("    enqueuing #" + node2orderIndex.get(child) + " " + child);					
			}
			
			int domainIdx = evidenceDomainIndices[nodeIdx];
			
			// get domain exclusions
			boolean[] excluded = domExclusions.get(nodeIdx);
			
			// for evidence nodes, we can continue if the evidence
			// probability was non-zero
			if(domainIdx >= 0) {
				s.nodeDomainIndices[nodeIdx] = domainIdx;
				samplingProb[nodeIdx] = 1.0;
				double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
				if(prob != 0.0) {
					valueSuccessfullyAssigned = true;
				}
			}
			
			// for non-evidence nodes, do forward sampling
			else {
				SampledAssignment sa = sampleForward(nodes[nodeIdx], s.nodeDomainIndices, excluded);
				if(sa != null) {
					domainIdx = sa.domIdx;
					samplingProb[nodeIdx] = sa.probability;
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					valueSuccessfullyAssigned = true;
				}
			}
			
			if(valueSuccessfullyAssigned) {
				// if we are backtracking and could assign a value, 
				// we are done backtracking and can continue processing the actual queue 
				backtracking = false;
			}
			else {
				// if we are already backtracking, we are done treating the "bottom" node 
				if(backtracking)
					backtrack.remove();
				// if we weren't already backtracking, we need to start
				else						
					backtracking = true;
				s.trials++;
				
				// we definitely need to re-enqueue this node for later re-assignment
				queue.add(nodeIdx);

				// kill the node's exclusions, because when we return,
				// anything could work
				domExclusions.remove(nodeIdx);

				// add the node's non-evidence parents to the backtracking queue
				BeliefNode[] domprod = nodes[nodeIdx].getCPF().getDomainProduct();
				for(int i = 1; i < domprod.length; i++) {
					if(this.evidenceDomainIndices[getNodeIndex(domprod[i])] == -1) {
						if(!backtrack.contains(domprod[i]))
							backtrack.add(domprod[i]);
					}
				}
				
				// if we need to backtrack but there are nodes to reassign, there is a problem
				if(backtrack.isEmpty())
					throw new Exception("Could not find a sample with non-zero probability. Most likely, the evidence specified has 0 probability.");
				
				onInitiatedBacktracking();
			}
			
			// debug info
			if(debug) {
				int numex = 0;
				for(int j = 0; j < excluded.length; j++)
					if(excluded[j])
						numex++;
				System.out.printf("    step %d, node #%d '%s' (%d/%d exclusions)  ", currentStep, node2orderIndex.get(nodes[nodeIdx]), nodes[nodeIdx].getName(), numex, excluded.length);
				if(valueSuccessfullyAssigned)
					System.out.printf("assigned %d (%s)\n", domainIdx, nodes[nodeIdx].getDomain().getName(domainIdx));
				else {
					if(evidenceDomainIndices[nodeIdx] == -1)
						System.out.println("impossible case; backtracking...");
					else
						System.out.println("evidence with probability 0.0; backtracking...");
				}
			}
		}			
		
		// we found a sample, determine its weight
		for(int i = 0; i < this.nodes.length; i++) {
			s.weight *= getCPTProbability(nodes[i], s.nodeDomainIndices) / samplingProb[i];
		}
		return s;
	}
	
	protected void onInitiatedBacktracking() {
		
	}
}

