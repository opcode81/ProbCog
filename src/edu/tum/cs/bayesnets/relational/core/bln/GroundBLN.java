package edu.tum.cs.bayesnets.relational.core.bln;

import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.relational.core.RelationalNode;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.KnowledgeBase;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.tools.Pair;

public class GroundBLN extends AbstractGroundBLN {
	
	protected WorldVariables worldVars;
	protected PossibleWorld state;
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, Database db) {
		super(bln, db);
		worldVars = new WorldVariables();
	}
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, String databaseFile) throws Exception {
		super(bln, databaseFile);
		worldVars = new WorldVariables();
	}
	
	@Override
	protected void onAddGroundAtomNode(RelationalNode relNode, String[] params) {
		if(relNode.isBoolean())
			worldVars.add(new GroundAtom(relNode.getFunctionName(), params));
		else {
			// node is non-Boolean, so add one ground atom for each possible value
			Discrete dom = relNode.getDomain();
			String[] atomParams = new String[params.length+1];
			for(int i = 0; i < params.length; i++)
				atomParams[i] = params[i];
			for(int i = 0; i < dom.getOrder(); i++) {
				atomParams[atomParams.length-1] = dom.getName(i);
				GroundAtom ga = new GroundAtom(relNode.getFunctionName(), atomParams.clone());
				worldVars.add(ga);
			}
		}
	}
	
	@Override	
	public void groundFormulaicNodes() throws Exception {
		state = new PossibleWorld(worldVars);
		BayesianLogicNetwork bln = (BayesianLogicNetwork)this.bln;
		KnowledgeBase gkb = bln.kb.ground(this.db, this.worldVars);
		System.out.printf("    %d formulas resulted in %s ground formulas\n", bln.kb.size(), gkb.size());
		int i = 0;
		for(Formula gf : gkb) {
			// add node and connections
			String nodeName = "GF" + i++;
			System.out.printf("    %s: %s\n", nodeName, gf.toString());
			HashSet<GroundAtom> gas = new HashSet<GroundAtom>();
			gf.getGroundAtoms(gas);
			Vector<String> parentGAs = new Vector<String>();
			for(GroundAtom ga : gas) {
				if(ga == null)
					throw new Exception("null ga encountered");
				parentGAs.add(ga.toString());
			}
			Pair<BeliefNode, BeliefNode[]> nodeData = addHardFormulaNode(nodeName, parentGAs);
			// set cpf
			fillFormulaCPF(gf, nodeData.first.getCPF(), nodeData.second, parentGAs);
			this.cpfIDs.put(nodeData.first, "F" + gkb.getTemplateID(gf));
		}
		state = null;
	}
	
	/**
	 * fills the CPF of a formulaic node
	 * @param gf	the ground formula to evaluate for all possible settings
	 * @param cpf	the CPF of the formulaic node to fill
	 * @param parents	the parents of the formulaic node
	 * @param parentGAs	the ground atom string names of the parents (in case the node names do not match them)
	 * @throws Exception
	 */
	protected void fillFormulaCPF(Formula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		assert parents.length == addr.length;
		fillFormulaCPF(gf, cpf, parents, parentGAs, 0, addr);
	}
	
	protected void fillFormulaCPF(Formula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs, int iParent, int[] addr) throws Exception {
		// if all parents have been set, determine the truth value of the formula and 
		// fill the corresponding column of the CPT 
		if(iParent == parents.length) {
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
		// otherwise get the next ground atom and consider all of its groundings
		BeliefNode parent = parents[iParent];
		String parentGA = parentGAs.get(iParent);
		Discrete domain = (Discrete)parent.getDomain();
		boolean isBoolean = RelationalBeliefNetwork.isBooleanDomain(domain);		
		// - get the domain index that corresponds to setting the atom to true
		int trueIndex = 0;
		if(!isBoolean) {
			int iStart = parentGA.lastIndexOf(',')+1;
			int iEnd = parentGA.lastIndexOf(')');
			String outcome = parentGA.substring(iStart, iEnd);
			trueIndex = domain.findName(outcome);
			if(trueIndex == -1) 
				throw new Exception("'" + outcome + "' not found in domain of " + parentGA);			
		}
		// - recursively consider all settings
		for(int i = 0; i < domain.getOrder(); i++) {
			// TODO make this faster -- contrary to my expectations, the order of the connect calls does not cause there to be a corresponding ordering in the CPF (seems arbitrary, possibly node index or something)
			// find the correct address index
			BeliefNode[] domProd = cpf.getDomainProduct();
			int iDomProd = -1;
			for(int j = 1; j < domProd.length; j++)
				if(domProd[j] == parent) {
					iDomProd = j;
					break;
				}
			if(iDomProd == -1)
				throw new Exception("Parent could not be found in domain product.");
			// set address 
			addr[iDomProd] = i;
			// set state for logical reasoner
			if(i == trueIndex)
				state.set(parentGA, true);
			else
				state.set(parentGA, false);
			// recurse
			fillFormulaCPF(gf, cpf, parents, parentGAs, iParent+1, addr);
		}
	}
}
