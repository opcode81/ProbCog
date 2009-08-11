package edu.tum.cs.srl.bayesnets.bln;

import java.util.HashMap;
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
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.tools.Pair;
import edu.tum.cs.tools.StringTool;

/**
 * represents a grounded Bayesian logic network (i.e. a mixed network)
 * @author jain
 */
public class GroundBLN extends AbstractGroundBLN {
	
	protected WorldVariables worldVars;
	protected PossibleWorld state;
	/**
	 * grounded knowledge base of hard constraints
	 */
	protected KnowledgeBase gkb;
	/**
	 * maps (non-auxiliary) belief nodes to the corresponding logical variable coupler
	 */
	protected HashMap<BeliefNode, IVariableLogicCoupler> variable2groundAtomLookup;
	protected HashMap<GroundAtom, BeliefNode> groundAtom2variable;
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, Database db) {
		super(bln, db);
	}
	
	public GroundBLN(AbstractBayesianLogicNetwork bln, String databaseFile) throws Exception {
		super(bln, databaseFile);
	}
	
	@Override
	protected void init(AbstractBayesianLogicNetwork bln, Database db) {
		super.init(bln, db);
		worldVars = new WorldVariables();
		variable2groundAtomLookup = new HashMap<BeliefNode, IVariableLogicCoupler>();
		groundAtom2variable = new HashMap<GroundAtom, BeliefNode>();
	}
	
	@Override
	protected void onAddGroundAtomNode(RelationalNode relNode, String[] params, BeliefNode var) {
		if(relNode.isBoolean()) {
			GroundAtom ga = new GroundAtom(relNode.getFunctionName(), params);
			worldVars.add(ga);
			variable2groundAtomLookup.put(var, new BooleanVariable(ga));
			groundAtom2variable.put(ga, var);
		}
		else {
			// node is non-Boolean, so add one block containing the ground atoms for each possible value
			Discrete dom = relNode.getDomain();
			String[] atomParams = new String[params.length+1];
			for(int i = 0; i < params.length; i++)
				atomParams[i] = params[i];
			Vector<GroundAtom> block = new Vector<GroundAtom>(dom.getOrder());
			for(int i = 0; i < dom.getOrder(); i++) {
				atomParams[atomParams.length-1] = dom.getName(i);
				GroundAtom ga = new GroundAtom(relNode.getFunctionName(), atomParams.clone());
				block.add(ga);				
			} 
			Block b = worldVars.addBlock(block); 
			for(GroundAtom ga : b)
				groundAtom2variable.put(ga, var);
			variable2groundAtomLookup.put(var, new BlockVariable(b));
		}
	}
	
	/**
	 * couples the logical variables (ground atoms) with the actual variables (belief nodes)
	 * @author jain
	 *
	 */
	protected interface IVariableLogicCoupler {
		public int getValue(PossibleWorld w);		
		public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars);
	}
	
	protected static class BooleanVariable implements IVariableLogicCoupler {
		public int idxGndAtom;
		
		public BooleanVariable(GroundAtom ga) {
			this.idxGndAtom = ga.index;
		}
		
		public int getValue(PossibleWorld w) {			
			return w.get(idxGndAtom) ? 0 : 1; // True is first element
		}

		public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars) {
			GroundAtom ga = worldVars.get(idxGndAtom);
			return new GroundLiteral(domIdx == 0, ga);
		}		
	}
	
	protected static class BlockVariable implements IVariableLogicCoupler {
		protected Block block;
		
		public BlockVariable(Block b) {
			block = b;
		}
		
		public int getValue(PossibleWorld w) {
			int i = 0;
			for(GroundAtom ga : block) {
				if(ga.isTrue(w))
					return i;
				++i;
			}
			throw new RuntimeException("No true atom in block " + block);
		}

		public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars) {
			GroundAtom ga = block.get(domIdx);
			return new GroundLiteral(true, ga);
		}		
	}
	
	public GroundLiteral getGroundLiteral(BeliefNode var, int domIdx) {
		IVariableLogicCoupler vlc = variable2groundAtomLookup.get(var);
		if(vlc == null)
			throw new RuntimeException("Variable " + var + " has no logical coupling!");
		return vlc.getGroundLiteral(domIdx, worldVars);
	}
	
	@Override	
	protected void groundFormulaicNodes() throws Exception {
		state = new PossibleWorld(worldVars);
		BayesianLogicNetwork bln = (BayesianLogicNetwork)this.bln;
		gkb = bln.kb.ground(this.db, this.worldVars, true);
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
	
	/**
	 * gets the knowledge base of grounded hard logical constraints
	 * @return
	 */
	public KnowledgeBase getKB() {
		return gkb;
	}
	
	public WorldVariables getWorldVars() {
		return worldVars;
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
		return groundAtom2variable.get(gndAtom);
	}
	
	public int getVariableValue(BeliefNode var, PossibleWorld w) {
		return variable2groundAtomLookup.get(var).getValue(w);
	}

	/**
	 * 
	 * @param var
	 * @return true if the variable is not an auxiliary variable that was created for a logical constraint but corresponds directly to a variable upon which the possible worlds are defined
	 */
	public boolean isRegularVariable(BeliefNode var) {
		return variable2groundAtomLookup.containsKey(var);
	}
	
	/**
	 * gets the set of regular variables (i.e. non-auxiliary belief nodes, which do not correspond to logical constraints) 
	 * @return
	 */
	public Set<BeliefNode> getRegularVariables() {
		return variable2groundAtomLookup.keySet();
	}
	
	public Set<GroundAtom> getMappedGroundAtoms() {
		return groundAtom2variable.keySet();
	}
}
