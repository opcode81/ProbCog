/*
 * Created on Oct 27, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;
import edu.tum.cs.logic.Disjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.TrueFalse;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.ClausalKB;
import edu.tum.cs.logic.sat.Clause;
import edu.tum.cs.logic.sat.SampleSAT;
import edu.tum.cs.srl.AbstractVariable;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.StringVariable;
import edu.tum.cs.srl.bayesnets.bln.coupling.VariableLogicCoupling;

public class SATIS_BSampler extends BackwardSampling {

	VariableLogicCoupling coupling;
	/**
	 * the SAT sampler used to sample a (sub-)state in each iteration
	 */
	SampleSAT sat;
	/**
	 * variables whose values are determined by the SAT sampler
	 */
	Collection<BeliefNode> determinedVars;
	/**
	 * clausal KB of constraints that must be satisfied by the SAT sampler 
	 */
	ClausalKB ckb;
	
	/**
	 * constructs a SAT-IS backward sampler with a given SAT sampler, a given logical coupling and a known set of variables affected by the SAT sampler.
	 * This construction method is used for BLNs.
	 * @param bn
	 * @param sat the SAT sampler to use in each iteration
	 * @param coupling the logical coupling of the BN's variables
	 * @param determinedVars the set of variables affected by the SAT sampler, i.e. the variables that will be set if the SAT sampler is run
	 * @throws Exception 
	 */
	public SATIS_BSampler(BeliefNetworkEx bn, SampleSAT sat, VariableLogicCoupling coupling, Collection<BeliefNode> determinedVars) throws Exception {
		super(bn);			
		this.coupling = coupling;
		this.sat = sat;
		this.ckb = null; // not required for this construction method
		this.determinedVars = determinedVars;
	}
	
	/**
	 * constructs a SAT-IS backward sampler for use with (propositional) Bayesian networks, creating a logical coupling and the SAT sampler automatically (using all deterministic constraints in CPTs).
	 * @param bn
	 * @throws Exception
	 */
	public SATIS_BSampler(BeliefNetworkEx bn) throws Exception {
		super(bn);
	}
	
	@Override
	protected void _initialize() throws Exception {
		// build the variable-logic coupling if we don't have it yet
		if(coupling == null) {
			coupling = new VariableLogicCoupling();
			for(BeliefNode n : nodes) {
				coupling.addBlockVariable(n, (Discrete)n.getDomain(), n.getName(), new String[0]);
			}
		}
		// gather clausal KB based on deterministic constraints in CPTs
		if(ckb == null) {
			ckb = new ClausalKB();
			extendKBWithDeterministicConstraintsInCPTs(bn, coupling, ckb, null);
		}
		// get the set of variables that is determined by the sat sampler
		if(determinedVars == null) {
			determinedVars = new HashSet<BeliefNode>();
			for(Clause c : ckb) {
				for(GroundLiteral lit : c.lits) {
					BeliefNode var = coupling.getVariable(lit.gndAtom);
					if(var == null)
						throw new Exception("Could not find node corresponding to ground atom '" + lit.gndAtom.toString() + "' with index " + lit.gndAtom.index + "; set of mapped ground atoms is " + coupling.getCoupledGroundAtoms());
					determinedVars.add(var);
				}
			}
		}
		// build SAT sampler if we don't have it yet
		if(this.sat == null) {
			// build evidence database
			Vector<PropositionalVariable> evidence = new Vector<PropositionalVariable>();
			for(int i = 0; i < evidenceDomainIndices.length; i++)
				if(evidenceDomainIndices[i] != -1) {
					evidence.add(new PropositionalVariable(nodes[i].getName(), nodes[i].getDomain().getName(evidenceDomainIndices[i])));
				}
			// construct sampler
			WorldVariables worldVars = this.coupling.getWorldVars();
			sat = new SampleSAT(ckb, new PossibleWorld(worldVars), worldVars, evidence);
		}
		// pass on parameters
		sat.setDebugMode(this.debug);
	}
	
	/**
	 * 
	 * @param bn
	 * @param coupling
	 * @param ckb the clausal KB to extend
	 * @param db an evidence database with which to simplify the formulas obtained, or null if no simplification is to take place
	 * @throws Exception
	 */
	public static void extendKBWithDeterministicConstraintsInCPTs(BeliefNetworkEx bn, VariableLogicCoupling coupling, ClausalKB ckb, Database db) throws Exception {
		int size = ckb.size();
		System.out.print("gathering deterministic constraints from CPDs... ");
		for(BeliefNode node : bn.bn.getNodes()) {
			if(!coupling.hasCoupling(node))
				continue;
			CPF cpf = node.getCPF();
			BeliefNode[] domProd = cpf.getDomainProduct();
			int[] addr = new int[domProd.length];
			walkCPF4HardConstraints(coupling, cpf, addr, 0, ckb, db);
		}
		System.out.println((ckb.size()-size) + " constraints added");
	}
	
	protected static void walkCPF4HardConstraints(VariableLogicCoupling coupling, CPF cpf, int[] addr, int i, ClausalKB ckb, Database db) throws Exception {
		BeliefNode[] domProd = cpf.getDomainProduct();
		if(i == addr.length) {
			double p = cpf.getDouble(addr);
			if(p == 0.0) {
				GroundLiteral[] lits = new GroundLiteral[domProd.length]; 
				for(int k = 0; k < domProd.length; k++) {
					lits[k] = coupling.getGroundLiteral(domProd[k], addr[k]);
					lits[k].negate();
				}
				Formula f = new Disjunction(lits);
				if(db != null) {
					f = f.simplify(db);
					if(f instanceof TrueFalse)
						return;
				}
				ckb.addFormula(f);				
			}
			return;
		}
		for(int k = 0; k < domProd[i].getDomain().getOrder(); k++) {
			addr[i] = k;
			walkCPF4HardConstraints(coupling, cpf, addr, i+1, ckb, db);
		}
	}
	
	protected static class PropositionalVariable extends StringVariable {

		public PropositionalVariable(String varName, String value) {
			super(varName, new String[0], value);			
		}
		
		@Override
		public String getPredicate() {
			return this.functionName + "(" + value + ")";
		}

		@Override
		public boolean isBoolean() {			
			return false;
		}
	}
	
	@Override
	public void initSample(WeightedSample s) throws Exception {
		super.initSample(s);
		
		// run SampleSAT to find a configuration that satisfies all logical constraints
		sat.run();
		PossibleWorld state = sat.getState();
		
		// apply the state found by SampleSAT to the sample 
		for(BeliefNode var : determinedVars) {
			int domIdx = coupling.getVariableValue(var, state);
			s.nodeDomainIndices[this.getNodeIndex(var)] = domIdx;
			/*if(true) {				
				out.printf("%s = %s\n", var.toString(), var.getDomain().getName(domIdx));
			}*/
		}
	}
	
	/**
	 * gets the sampling order by filling the members for backward and forward sampled nodes as well as the set of nodes not in the sampling order
	 * @param evidenceDomainIndices
	 * @throws Exception 
	 */
	protected void getOrdering(int[] evidenceDomainIndices) throws Exception {
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
		
		// schedule all uninstantiated nodes for forward sampling in the topological order
		for(int i : topOrder) {
			if(uninstantiatedNodes.contains(nodes[i]))
				forwardSampledNodes.add(nodes[i]);
		}
		
		out.println("node ordering: " + outsideSamplingOrder.size() + " outside order, " + backwardSampledNodes.size() + " backward, " + forwardSampledNodes.size() + " forward");
	}
	
	@Override
	public String getAlgorithmName() {
		return String.format("%s[%s]", getClass().getSimpleName(), sat.getAlgorithmName());
	}
}