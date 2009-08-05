package edu.tum.cs.srl.bayesnets.inference;

import java.util.Arrays;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.BackwardSampling;
import edu.tum.cs.bayesnets.inference.Sampler;
import edu.tum.cs.bayesnets.inference.WeightedSample;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.sat.ClausalKB;
import edu.tum.cs.logic.sat.Clause;
import edu.tum.cs.logic.sat.SampleSAT;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;

/**
 * SAT-IS: satisfiability-based importance sampling for inference in mixed networks with probabilistic and deterministic constraints
 * 
 * @author jain
 */
public class SATIS extends BNSampler {

	GroundBLN gbln;
	/**
	 * the SAT sampler that is used to sample the sub-state that is determined by the hard logical constraints 
	 */
	SampleSAT ss;
	/**
	 * the set of nodes whose values are determined by the SAT sampler (because they are part of a hard logical constraint)
	 */
	HashSet<BeliefNode> determinedVars;
	
	public SATIS(GroundBLN bln) throws Exception {
		super(bln, SATIS_BSampler.class);
		gbln = bln;
		initSATSampler();
	}
	
	protected void initSATSampler() throws Exception {
		// create SAT sampler
		PossibleWorld state = new PossibleWorld(gbln.getWorldVars());
		ClausalKB ckb = getClausalKB();
		ss = new SampleSAT(ckb, state, gbln.getWorldVars(), gbln.getDatabase());
		
		// get the set of variables that is determined by the sat sampler
		determinedVars = new HashSet<BeliefNode>();
		for(Clause c : ckb) {
			for(GroundLiteral lit : c.lits) {
				determinedVars.add(gbln.getVariable(lit.gndAtom));
			}
		}		
	}
	
	protected ClausalKB getClausalKB() throws Exception {
		return new ClausalKB(gbln.getKB());
	}
	
	@Override
	protected Sampler getSampler() {
		return new SATIS_BSampler(gbln.getGroundNetwork());
	}	
	

	public class SATIS_BSampler extends BackwardSampling {

		public SATIS_BSampler(BeliefNetworkEx bn) {
			super(bn);			
		}
		
		@Override
		public void initSample(WeightedSample s) {
			super.initSample(s);
			
			// run SampleSAT to find a configuration that satisfies all logical constraints
			ss.run();
			PossibleWorld state = ss.getState();
			
			// apply the state found by SampleSAT to the sample 
			for(BeliefNode var : determinedVars) { 				
				s.nodeDomainIndices[this.getNodeIndex(var)] = gbln.getVariableValue(var, state);
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

			// remove logically determined nodes from the set of uninstantiated nodes
			// and store them as outside the sampling order so their conditional probability is considered in the sample weight
			for(BeliefNode n : determinedVars) {
				uninstantiatedNodes.remove(n);
				outsideSamplingOrder.add(n);
			}
			
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
			
			System.out.println("node ordering: " + outsideSamplingOrder.size() + " outside order, " + backwardSampledNodes.size() + " backward, " + forwardSampledNodes.size() + " forward");
		}
	}
}
