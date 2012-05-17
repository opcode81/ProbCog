package probcog.srl.directed.bln.py;

import java.util.Collection;
import java.util.Vector;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.srl.Database;
import probcog.srl.Signature;
import probcog.srl.directed.RelationalBeliefNetwork;
import probcog.srl.directed.bln.AbstractGroundBLN;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.CPT;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.util.Stopwatch;

public class GroundBLN extends AbstractGroundBLN {
	protected BeliefNetworkEx groundBN;
	protected Vector<String> hardFormulaNodes;
	protected Database db;
	
	public GroundBLN(BayesianLogicNetworkPy bln, String databaseFile) throws Exception {
		super(bln, databaseFile);
	}
	
	public GroundBLN(BayesianLogicNetworkPy bln, Database db) throws Exception {
		super(bln, db);
	}
	
	@Override
	public void groundFormulaicNodes() throws Exception {
		BayesianLogicNetworkPy bln = (BayesianLogicNetworkPy)this.bln;
		// ground formulaic nodes
		System.out.println("    grounding formulas...");
		bln.generateGroundFormulas(this.databaseFile);
		GroundFormulaIteration gfIter = bln.iterGroundFormulas();
		System.out.printf("      %d formulas instantiated\n", gfIter.getCount());
		System.out.println("    instantiating nodes and CPFs...");
		Stopwatch sw_structure = new Stopwatch();
		Stopwatch sw_cpt = new Stopwatch();
		for(GroundFormula gf : gfIter) {
			// create a node for the ground formula
			sw_structure.start();
			String nodeName = "GF" + gf.idxGF;
			System.out.println(nodeName + ": " + gf);
			Vector<String> GAs = gf.getGroundAtoms();
			BeliefNode node = addHardFormulaNode(nodeName, GAs);
			// add edges from ground atoms						
			sw_structure.stop();
			// fill CPT according to formula semantics
			// TODO try to reuse CPFs generated for previous formulas with same formula index
			sw_cpt.start();
			fillFormulaCPF(gf, node.getCPF(),GAs);
			sw_cpt.stop();
		}
		System.out.println("    structure time: " + sw_structure.getElapsedTimeSecs() + "s");
		System.out.println("    cpf time: " + sw_cpt.getElapsedTimeSecs() + "s");		
	}
	
	/**
	 * adds a node corresponding to a hard constraint to the network - along with the necessary edges
	 * @param nodeName  	name of the node to add for the constraint
	 * @param parentGAs		collection of names of parent nodes/ground atoms 
	 * @return the node that was added
	 * @throws Exception
	 */
	public BeliefNode addHardFormulaNode(String nodeName, Collection<String> parentGAs) throws Exception {
		BeliefNode[] domprod = new BeliefNode[1+parentGAs.size()];
		BeliefNode node = groundBN.addNode(nodeName);
		domprod[0] = node;
		hardFormulaNodes.add(node.getName());
		int i = 1;
		for(String strGA : parentGAs) {
			BeliefNode parent = groundBN.getNode(strGA);
			if(parent == null) { // if the atom cannot be found, e.g. attr(X,Value), it might be a functional, so remove the last argument and try again, e.g. attr(X) (=Value)
				String parentName = strGA.substring(0, strGA.lastIndexOf(",")) + ")";
				parent = groundBN.getNode(parentName);
				if(parent == null)
					throw new Exception("Could not find node for ground atom " + strGA);
			}
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
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, Vector<String> parentGAs) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		fillFormulaCPF(gf, cpf, parentGAs, 1, addr);
	}
	
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, Vector<String> parentGAs, int iDomProd, int[] addr) throws Exception {
		BeliefNode[] domprod = cpf.getDomainProduct();
		// if all parents have been set, determine the truth value of the formula and 
		// fill the corresponding column of the CPT 
		State state = ((BayesianLogicNetworkPy)bln).getState();
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
	 * gets the ground Bayesian network
	 * @return
	 */
	public BeliefNetworkEx getGroundBN() {
		return this.groundBN;
	}
	
	@Override
	protected void onAddGroundAtomNode(BeliefNode instance, String[] params,
			Signature sig) {
		// TODO Auto-generated method stub
		
	}
}
