package edu.tum.cs.srl.bayesnets.inference;

import java.util.HashMap;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SampleSearch;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;

public class SampleSearch2 extends BNSampler {
	/**
	 * equivalence classes
	 */
	HashMap<BeliefNode, Integer> node2class = new HashMap<BeliefNode, Integer>();

	public SampleSearch2(GroundBLN gbln) throws Exception {
		super(gbln, SampleSearchInference.class);

		computeEquivalenceClasses();

		// TODO generate relations between nodes with respect to the ordering
	}

	protected void computeEquivalenceClasses() throws Exception {
		BeliefNetworkEx bn = gbln.getGroundNetwork();

		// get topological ordering
		TopologicalSort sort = new TopologicalSort(bn.bn);
		TopologicalOrdering ordering = sort.run(false);

		// determine equivalence classes
		BeliefNode[] nodes = bn.getNodes();
		Integer numericID = 0;
		HashMap<String, Integer> classes = new HashMap<String, Integer>();
		int idxTier = 0;
		for(Vector<Integer> tier : ordering.getTiers()) {
			for(Integer nodeIdx : tier) {
				BeliefNode node = nodes[nodeIdx];
				BeliefNode[] clique = node.getCPF().getDomainProduct();
				StringBuffer sb = new StringBuffer(gbln.getCPFID(clique[0]));
				for(int i = 1; i < clique.length; i++) {
					sb.append("-");
					sb.append(node2class.get(clique[i]));
				}
				sb.append("-" + idxTier); // TODO: not sure if this is
				// absolutely necessary, but I
				// currently think that it is ;)
				String classID = sb.toString();
				Integer classNum = classes.get(classID);
				if(classNum == null)
					classes.put(classID, classNum = numericID++);
				node2class.put(node, classNum);
				System.out.printf("%s: %d\n", node.getName(), classNum);
			}
			idxTier++;
		}
	}

	protected static class SampleSearchInference extends SampleSearch {

		public SampleSearchInference(BeliefNetworkEx bn) throws Exception {
			super(bn);
		}

		@Override
		public int[] getNodeOrdering() {
			// TODO
			return super.getNodeOrdering();
		}
/*
		public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
			s.trials = 0;
			s.weight = 1.0;
			s.trials++;
			double[] samplingProb = new double[nodeOrder.length];

			LinkedList<Integer> queue = new LinkedList<Integer>();
			for(int i : nodeOrder)
				queue.add(i);
			
			LinkedList<BeliefNode> backtracking = new LinkedList<BeliefNode>(); 

			// assign values to the nodes in order
			HashMap<Integer, boolean[]> domExclusions = new HashMap<Integer, boolean[]>();
			while(!queue.isEmpty()) {
				int nodeIdx;
				
				if(backtracking.isEmpty()) {
					nodeIdx = queue.remove();
				}
				else {
					BeliefNode child = backtracking.remove();
					queue.add(this.getNodeIndex(child));
					
					int childNodeIdx = this.getNodeIndex(child);
						
					// kill the node's exclusions, because when we return,
					// anything could work
					domExclusions.remove(childNodeIdx);
					
					// get the parents
				}
				
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
					for(int j = 0; j < excluded.length; j++)
						if(excluded[j])
							numex++;
					System.out.printf("    step %d, node %d '%s' (%d/%d exclusions)\n", currentStep, i, nodes[nodeIdx].getName(), numex, excluded.length);
				}
				
				// for evidence nodes, we can continue if the evidence
				// probability was non-zero
				if(domainIdx >= 0) {
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					samplingProb[nodeIdx] = 1.0;
					double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
					if(prob != 0.0) {
						continue;
					}
					else {
						if(debug)
							System.out.println("      evidence with probability 0.0; backtracking...");
					}
				}
				
				// for non-evidence nodes, do forward sampling
				else {
					SampledAssignment sa = sampleForward(nodes[nodeIdx], s.nodeDomainIndices, excluded);
					if(sa != null) {
						domainIdx = sa.domIdx;
						samplingProb[nodeIdx] = sa.probability;
						s.nodeDomainIndices[nodeIdx] = domainIdx;
						continue;
					}
					else if(debug)
						System.out.println("      impossible case; backtracking...");
				}
				
				// if we get here, we need to backtrack to the last non-evidence
				// node
				// TODO better: backtrack to last (non-evidence) parent of
				// current node
				s.trials++;
				do {
					
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
			
			
			
			// we found a sample, determine its weight
			for(int i = 0; i < this.nodes.length; i++) {
				s.weight *= getCPTProbability(nodes[i], s.nodeDomainIndices) / samplingProb[i];
			}
			return s;
		}
		*/

	}
}
