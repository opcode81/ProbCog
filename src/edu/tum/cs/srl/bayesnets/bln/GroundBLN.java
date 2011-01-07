package edu.tum.cs.srl.bayesnets.bln;

import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Value;
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
import edu.tum.cs.util.datastruct.OrderedSet;

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
		gkb = bln.kb.ground(this.db, worldVars, useFormulaSimplification); 
		if(verbose) System.out.printf("    %d formulas resulted in %s ground formulas\n", bln.kb.size(), gkb.size());
		HashMap<String, Value[]> cpfCache = new HashMap<String, Value[]>();
		int i = 0;
		for(Formula gf : gkb) {			
			if(!useFormulaSimplification)
				gf = gf.simplify(null); // still do basic simplification (there may still be TrueFalse instances due to equalities, e.g. !(x=y))
			
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
			Vector<String> parentGAs = new Vector<String>();
			for(GroundAtom ga : gas) {
				if(ga == null)
					throw new Exception("null ground atom encountered");
				parentGAs.add(ga.toString());
			}
			BeliefNode node = addHardFormulaNode(nodeName, parentGAs); // this establishes connections and initialises the CPF
			
			// set CPF id (i.e. equivalence class id)
			// TODO try string transform: Two formulas are equivalent if they are the same except for the universally quantified variables
			String cpfid; 
			if(useFormulaSimplification) {
				cpfid = "F" + i; // treat all formulas differently
			}
			else
				cpfid = "F" + gkb.getTemplateID(gf); // treat all instances of a formula template the same	
			this.cpfIDs.put(node, cpfid);

			// set CPF
			Value[] values = cpfCache.get(cpfid);
			if(values != null) {
				// Note: A value from the cache can only be used if formula simplification is not applied,
				//		 because the CPF ids are never equal otherwise.
				// TODO Is this really 100% safe? What about the case where constants appear in the formula, e.g. in "(x=Const)"? The above-mentioned string transform might be safer. 
				node.getCPF().setValues(values);
			}
			else {
				fillFormulaCPF(gf, node.getCPF(), parentGAs);
			}
			
			++i;
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
	protected void fillFormulaCPF(Formula gf, CPF cpf, Vector<String> parentGAs) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		assert parentGAs.size() == addr.length-1 : "Address length: " + addr.length + " but number of parents is " + parentGAs.size();
		fillFormulaCPF(gf, cpf, parentGAs, 1, addr);
	}
	
	protected void fillFormulaCPF(Formula gf, CPF cpf, Vector<String> parentGAs, int iDomProd, int[] addr) throws Exception {
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
		// otherwise get the next ground atom and consider all of its groundings
		BeliefNode parent = domprod[iDomProd];
		String parentGA = parentGAs.get(iDomProd-1);
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
			// set address 
			addr[iDomProd] = i;
			// set state for logical reasoner
			if(i == trueIndex)
				state.set(parentGA, true);
			else
				state.set(parentGA, false);
			// recurse
			fillFormulaCPF(gf, cpf, parentGAs, iDomProd+1, addr);
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
