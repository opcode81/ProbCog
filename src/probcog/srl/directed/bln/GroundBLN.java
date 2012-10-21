/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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
package probcog.srl.directed.bln;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import probcog.logic.Formula;
import probcog.logic.GroundAtom;
import probcog.logic.GroundLiteral;
import probcog.logic.KnowledgeBase;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.logic.Formula.FormulaSimplification;
import probcog.srl.Database;
import probcog.srl.Signature;
import probcog.srl.directed.bln.coupling.VariableLogicCoupling;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.CPT;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Value;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.OrderedSet;

/**
 * Represents a grounded Bayesian logic network (i.e. a mixed network)
 * @author Dominik Jain
 */
public class GroundBLN extends AbstractGroundBLN {
	
	protected VariableLogicCoupling coupling;	
	/**
	 * possible world (used only temporarily during instantiation)
	 */
	protected PossibleWorld state;
	/**
	 * grounded knowledge base of hard constraints
	 */
	protected KnowledgeBase gkb;
	/**
	 * whether to simplify any ground formulas as far as possible
	 * TODO: maybe simplification should depend on the algorithm that is used
	 */
	protected boolean useFormulaSimplification = false;
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, Database db) throws Exception {
		super(bln, db);
	}
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, String databaseFile) throws Exception {
		super(bln, databaseFile);
	}
	
	@Override
	protected void init(AbstractBayesianLogicNetwork bln, Database db) throws Exception {
		super.init(bln, db);
		coupling = new VariableLogicCoupling();
		this.paramHandler.add("simplifyFormulas", "setFormulaSimplification");
	}
	
	public void setFormulaSimplification(boolean enabled) {
		useFormulaSimplification = enabled;
	}
	
	@Override
	protected void onAddGroundAtomNode(BeliefNode var, String[] params, Signature sig) {
		if(sig.isBoolean()) {			
			coupling.addBooleanVariable(var, sig.functionName, params);
		}
		else {
			// node is non-Boolean, so add one block containing the ground atoms for each possible value
			coupling.addBlockVariable(var, (Discrete)var.getDomain(), sig.functionName, params);
		}
	}
	
	@Override
	protected void onAddAuxiliaryNode(BeliefNode var, boolean isBoolean, String functionName, String[] params) {
		if(isBoolean) {			
			coupling.addBooleanVariable(var, functionName, params);
		}
		else {
			// node is non-Boolean, so add one block containing the ground atoms for each possible value
			coupling.addBlockVariable(var, (Discrete)var.getDomain(), functionName, params);
		}
	}
	
	public GroundLiteral getGroundLiteral(BeliefNode var, int domIdx) {
		return coupling.getGroundLiteral(var, domIdx);
	}
	
	@Override	
	protected void groundFormulaicNodes() throws Exception {
		WorldVariables worldVars = coupling.getWorldVars();
		state = new PossibleWorld(worldVars);
		BayesianLogicNetwork bln = (BayesianLogicNetwork)this.bln;
		gkb = bln.kb.ground(this.db, worldVars, useFormulaSimplification ? FormulaSimplification.OnDisallowFalse : FormulaSimplification.None); 
		if(verbose) System.out.printf("    %d formulas resulted in %s ground formulas\n", bln.kb.size(), gkb.size());
		HashMap<String, Value[]> cpfCache = new HashMap<String, Value[]>();
		int i = 0;
		for(Formula gf : gkb) {			
			// get the template from which the ground formula was instantiated (after simplification, we can't retrieve it)		
			Integer templateID = gkb.getTemplateID(gf);
			assert templateID != null : "Ground formula " + gf + " has no template ID";
			
			// if formulas weren't fully simplified, still apply basic simplification (i.e. without using the database)
			// note: there may still be TrueFalse instances due to equalities, e.g. !(x=y))
			if(!useFormulaSimplification)
				gf = gf.simplify(null); 
			
			// add node and connections
			String nodeName = "GF" + i;
			if(verbose) System.out.printf("    %s: %s\n", nodeName, gf.toString());
			// NOTE: we use an ordered set to guarantee that the ordering of nodes is the same 
			//       across all instances of a formula template; such that (if formulas are not
			//       simplified using the evidence) we could use the same CPF for all of the
			//       instances of a formula
			Set<GroundAtom> gas = new OrderedSet<GroundAtom>();  
			gf.getGroundAtoms(gas);
			//System.out.printf("      referenced ground atoms in GF%d: %s\n", i, StringTool.join(", ", gas));
			OrderedSet<BeliefNode> parents = new OrderedSet<BeliefNode>(); // use ordered set here, too, because several ground atoms may map to the same variable (e.g. foo(a,b), foo(a,c) -> foo(a))
			for(GroundAtom ga : gas) {
				if(ga == null)
					throw new Exception("null ground atom encountered");
				String strGA = ga.toString();
				BeliefNode parent = groundBN.getNode(strGA);
				if(parent == null) { // if the atom cannot be found, e.g. attr(X,Value), it might be a functional, so remove the last argument and try again, e.g. attr(X) (=Value)
					String parentName = strGA.substring(0, strGA.lastIndexOf(",")) + ")";
					parent = groundBN.getNode(parentName);
					if(parent == null)
						throw new Exception("Could not find node for ground atom " + strGA);
				}				
				parents.add(parent);
			}
			BeliefNode node = addHardFormulaNode(nodeName, parents); // this establishes connections and initialises the CPF
			
			// set CPF id (i.e. equivalence class id)
			// TODO try string transform: Two formulas are equivalent if they are the same except for the universally quantified variables
			String cpfid; 
			if(useFormulaSimplification) { // treat all formulas differently
				cpfid = "F" + i; 
			}
			else { // treat all instances of a formula template the same
				cpfid = "F" + templateID; 
			}
			this.cpfIDs.put(node, cpfid);

			// set CPF
			Value[] values = cpfCache.get(cpfid);
			if(values != null) {
				// Note: A value from the cache can only be used if formula simplification is not applied,
				//		 because the CPF ids are never equal otherwise.
				// TODO Is this really 100% safe? What about the case where constants appear in the formula, e.g. in "(x=Const)"? The above-mentioned string transform might be safer. 
				((CPT)node.getCPF()).setValues(values);
			}
			else {
				fillFormulaCPF(gf, node.getCPF());
			}
			
			++i;
		}
		// clean up
		state = null;
	}
	
	/**
	 * adds a node corresponding to a hard constraint to the network - along with the necessary edges
	 * @param nodeName  	name of the node to add for the constraint
	 * @param parentGAs		collection of names of parent nodes/ground atoms 
	 * @return the node that was added
	 * @throws Exception
	 */
	public BeliefNode addHardFormulaNode(String nodeName, Collection<BeliefNode> parents) throws Exception {
		BeliefNode[] domprod = new BeliefNode[1+parents.size()];
		BeliefNode node = groundBN.addNode(nodeName);
		domprod[0] = node;
		hardFormulaNodes.add(node);
		int i = 1;
		for(BeliefNode parent : parents) {
			domprod[i++] = parent;
			groundBN.connect(parent, node, false);
		}
		((CPT)node.getCPF()).buildZero(domprod, false); // ensure correct ordering in CPF
		return node;
	}
	
	/**
	 * fills the CPF of a formulaic node
	 * @param gf	the ground formula to evaluate for all possible settings
	 * @param cpf	the CPF of the formulaic node to fill
	 * @param parents	the parents of the formulaic node
	 * @param parentGAs	the ground atom string names of the parents (in case the node names do not match them)
	 * @throws Exception
	 */
	protected void fillFormulaCPF(Formula gf, CPF cpf) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		fillFormulaCPF(gf, cpf, 1, addr);
	}
	
	protected void fillFormulaCPF(Formula gf, CPF cpf, int iDomProd, int[] addr) throws Exception {
		BeliefNode[] domprod = cpf.getDomainProduct();
		// if all parents have been set, determine the truth value of the formula and 
		// fill the corresponding column of the CPT 
		if(iDomProd == domprod.length) {
			// get truth value of formula
			double value = gf.isTrue(state) ? 1 : 0;
			/*
			for(String ga : parentGAs)
				System.out.print(ga + " = " + state.get(ga) + ", ");
			System.out.println(" -> " + value);
			*/
			// write to CPF
			// - true
			addr[0] = 0;
			cpf.put(addr, new ValueDouble(value));
			// - false
			addr[0] = 1;
			cpf.put(addr, new ValueDouble(1.0-value));
			return;
		}
		// otherwise get the next parent and consider all of its settings
		BeliefNode parent = domprod[iDomProd];		
		int domOrder = parent.getDomain().getOrder();
		// - recursively consider all settings
		for(int i = 0; i < domOrder; i++) {			
			// set address 
			addr[iDomProd] = i;
			// set state for logical reasoner
			coupling.setVariableValue(parent, i, state);
			// recurse
			fillFormulaCPF(gf, cpf, iDomProd+1, addr);
		}
	}
	
	/**
	 * gets the knowledge base of grounded hard logical constraints
	 * @return
	 */
	public KnowledgeBase getKB() {
		return gkb;
	}
	
	public WorldVariables getWorldVars() {
		return coupling.getWorldVars();
	}
	
	/**
	 * gets the variable name in the ground network that corresponds to the given logical ground atom
	 * @param gndAtom
	 * @return
	 */
	public String getVariableName(GroundAtom gndAtom) {
		if(bln.rbn.isBoolean(gndAtom.predicate)) 
			return gndAtom.toString();				
		else 
			return gndAtom.predicate + "(" + StringTool.join(",", gndAtom.args, 0, gndAtom.args.length-1) + ")";		
	}
	
	/**
	 * returns the belief node in the ground network that corresponds to the given ground atom
	 * @param gndAtom
	 * @return the belief node corresponding to gndAtom or null if no correspondence is found 
	 */
	public BeliefNode getVariable(GroundAtom gndAtom) {
		return coupling.getVariable(gndAtom);
	}
	
	public int getVariableValue(BeliefNode var, PossibleWorld w) {
		return coupling.getVariableValue(var, w);
	}

	/**
	 * 
	 * @param var
	 * @return true if the variable is not an auxiliary variable that was created for a logical constraint but corresponds directly to a variable upon which the possible worlds are defined
	 */
	public boolean isRegularVariable(BeliefNode var) {
		return coupling.hasCoupling(var);
	}
	
	/**
	 * gets the set of regular variables (i.e. non-auxiliary belief nodes, which do not correspond to logical constraints) 
	 * @return
	 */
	public Set<BeliefNode> getRegularVariables() {
		return coupling.getCoupledVariables();
	}
	
	public VariableLogicCoupling getCoupling() {
		return coupling;
	}
}
