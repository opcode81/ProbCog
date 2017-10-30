/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.bayesnets.inference;

import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.exception.ProbCogException;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.util.datastruct.PrioritySet;

/**
 * SampleSearch with backjumping
 * @author Dominik Jain
 */
public class SampleSearchBJ extends SampleSearch {
	protected HashMap<BeliefNode, Integer> node2orderIndex;
	
	public SampleSearchBJ(BeliefNetworkEx bn) throws ProbCogException {
		super(bn);			
	}

	@Override
	protected int[] computeNodeOrdering() throws ProbCogException {
		if(verbose)
			System.out.println("computing node ordering...");
		
		/*
		TopologicalOrdering topologicalOrdering = new TopologicalSort(this.bn.bn).run();
		order = new int[nodes.length];
		int i = 0;
		for(int nodeIdx : topologicalOrdering)
			order[i++] = nodeIdx;
		*/			
		// this ordering seems to work slightly better than the above in practice
		int[] samplingOrder = bn.getTopologicalOrder(); 
		
		// maintain mapping of node to index in ordering
		node2orderIndex = new HashMap<BeliefNode, Integer>();
		for(int i = 0; i < samplingOrder.length; i++) {
			node2orderIndex.put(nodes[samplingOrder[i]], i);
		}
		
		return samplingOrder;
	}
	
	protected static final class HighestFirst implements Comparator<Integer> {
		
		public HighestFirst() {}

		@Override
		public int compare(Integer o1, Integer o2) {
			return -o1.compareTo(o2);
		}		
	}
	
	public class DomainExclusions {
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
		
		public int getNumExclusions(Integer nodeIdx) {
			boolean[] excl = domExclusions.get(nodeIdx);
			if(excl == null) 
				return 0;
			int n = 0;
			for(boolean b : excl)
				if(b)
					++n;
			return n;
		}
	}

	@Override
	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws ProbCogException {
		s.trials = 1;
		s.operations = 0;
		s.weight = 1.0;

		//PriorityQueue<BeliefNode> backtrack = new PriorityQueue<BeliefNode>(10, new BacktrackOrderingComparator());
		boolean backtracking = false;
		HighestFirst highestFirst = new HighestFirst();
		DomainExclusions domExclusions = new DomainExclusions();
		HashMap<Integer,PrioritySet<Integer>> backtrackQueues = new HashMap<Integer,PrioritySet<Integer>>();

		// assign values to the nodes in order
		for(int orderIdx = 0; orderIdx < nodeOrder.length;) {
			s.operations++;			
			boolean valueSuccessfullyAssigned = false;
			
			int nodeIdx = nodeOrder[orderIdx];
			
			if(!debug && infoInterval == 1)
				System.out.printf("  #%d  \r", orderIdx);
			
			if(!backtracking) {
				domExclusions.remove(nodeIdx);				
				backtrackQueues.remove(orderIdx);
			}
			else {
				// since we are backtracking, the previous setting of this node is 
				// inapplicable, so we add it to the exclusions
				domExclusions.add(nodeIdx, s.nodeDomainIndices[nodeIdx]);
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
				
				++orderIdx;
			}
			else {
				if(orderIdx == 0) // can't backtrack further
					throw new ProbCogException("Backtracking past first level. Most likely, the evidence that was specified is contradictory");
				
				backtracking = true;
				
				PrioritySet<Integer> backtrackQueue = backtrackQueues.get(orderIdx);
				if(backtrackQueue == null)
					backtrackQueue = new PrioritySet<Integer>(new PriorityQueue<Integer>(1, highestFirst));
				if(debug) System.out.printf("    initial backtrack queue: %s\n", backtrackQueue);
				
				// extend the queue depending on the current constraint:
				// add the non-evidence parents
				BeliefNode[] domprod = nodes[nodeIdx].getCPF().getDomainProduct();
				for(int j = 1; j < domprod.length; j++) {
					Integer level = node2orderIndex.get(domprod[j]);
					int parentNodeIdx = getNodeIndex(domprod[j]);
					if(evidenceDomainIndices[parentNodeIdx] < 0) {
						if(debug) System.out.printf("      adding %d\n", level, domprod[j].getName());
						backtrackQueue.add(level);
					}
				}
				
				// back jump
				Integer iprev = orderIdx;
				if(backtrackQueue.isEmpty())
					throw new ProbCogException("Nowhere left to backjump to from node #" + orderIdx + ". Most likely, the evidence has 0 probability.");
				else
					orderIdx = backtrackQueue.remove();
				
				assert orderIdx < iprev : "Invalid backjump from " + iprev + " to " + orderIdx;

				// undo all assignments along the way (necessary for backward sampling distributions to be constructed correctly)
				//for(int j = iprev-1; j >= orderIdx; j--)
				//	undoAssignment(j, s);
				
				// merge to update the new node's backtracking queue 
				PrioritySet<Integer> oldQueue = backtrackQueues.get(orderIdx);
				if(oldQueue == null) {
					//backtrackQueues.put(i, backtrackQueue); // unsafe? -- would assign same queue to i and i-1
					oldQueue = new PrioritySet<Integer>(new PriorityQueue<Integer>(1, highestFirst));
					backtrackQueues.put(orderIdx, oldQueue);
				}
				for(Integer j : backtrackQueue)
					oldQueue.add(j);
				
			}
			
			// debug info
			/*
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
			*/
		}			
 
		return s;
	}
}

