package edu.tum.cs.srl.bayesnets.bln;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.KnowledgeBase;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.srl.bayesnets.bln.coupling.VariableLogicCoupling;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;

/**
 * represents a grounded Bayesian logic network (i.e. a mixed network)
 * @author jain
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
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, Database db) {
		super(bln, db);
	}
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, String databaseFile) throws Exception {
		super(bln, databaseFile);
	}
	
	@Override
	protected void init(AbstractBayesianLogicNetwork bln, Database db) {
		super.init(bln, db);
		coupling = new VariableLogicCoupling();
	}
	
	@Override
	protected void onAddGroundAtomNode(RelationalNode relNode, String[] params, BeliefNode var) {
		if(relNode.isBoolean()) {			
			coupling.addBooleanVariable(var, relNode.getFunctionName(), params);
		}
		else {
			// node is non-Boolean, so add one block containing the ground atoms for each possible value
			coupling.addBlockVariable(var, relNode.getDomain(), relNode.getFunctionName(), params);
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
		gkb = bln.kb.ground(this.db, worldVars, true);
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
		// clean up
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
