package edu.tum.cs.bayesnets.relational.inference;

import java.util.Collection;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import org.python.core.PyObject.ConversionException;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.relational.core.*;
import edu.tum.cs.bayesnets.relational.core.BayesianLogicNetwork.GroundFormula;
import edu.tum.cs.bayesnets.relational.core.BayesianLogicNetwork.State;
import edu.tum.cs.bayesnets.relational.learning.Database;
import edu.tum.cs.tools.JythonInterpreter;

public class GroundBLN {
	protected BeliefNetworkEx groundBN;
	protected BayesianLogicNetwork bln;
	
	public GroundBLN(BayesianLogicNetwork bln, String databaseFile) throws Exception {
		this.bln = bln;
		Database db = new Database(bln.rbn);
		db.readBLOGDB(databaseFile);
		
		groundBN = new BeliefNetworkEx();
		
		// ground regular probabilistic nodes (i.e. ground atoms)
		RelationalBeliefNetwork rbn = bln.rbn;
		int[] order = rbn.getTopologicalOrder();
		for(int i = 0; i < order.length; i++) {
			int nodeNo = order[i];
			RelationalNode node = rbn.getRelationalNode(nodeNo);
			Collection<String[]> parameterSets = ParameterGrounder.generateGroundings(node, db);
			for(String[] params : parameterSets) {
				// add the node itself to the network
				String mainNodeName = node.getVariableName(params);
				groundBN.addNode(mainNodeName);
				// add edges from the parents
				ParentGrounder pg = rbn.getParentGrounder(node);
				Vector<Map<Integer, String[]>> groundings = pg.getGroundings(params, db);
				if(groundings.size() != 1) {
					System.err.println("Warning: Cannot ground structure because of multiple parent sets for node " + mainNodeName);
					continue;
				}
				Map<Integer, String[]> grounding = groundings.firstElement();
				for(Entry<Integer, String[]> entry : grounding.entrySet()) {
					if(entry.getKey() != nodeNo) {
						RelationalNode parent = rbn.getRelationalNode(entry.getKey());
						groundBN.connect(parent.getVariableName(entry.getValue()), mainNodeName);
					}
				}
			}
		}		
		
		// ground formulaic nodes
		bln.generateGroundFormulas(databaseFile);
		for(GroundFormula gf : bln.iterGroundFormulas()) {
			Vector<String> vGA = gf.getGroundAtoms();
			System.out.println(vGA);
			// create a node for the ground formula
			String nodeName = "GF" + gf.idxGF;
			BeliefNode node = groundBN.addNode(nodeName);
			// add edges from ground atoms
			for(String strGA : gf.getGroundAtoms()) {
				groundBN.connect(strGA, nodeName);
			}
			// fill CPT according to formula semantics
			// TODO reuse CPFs generated for previous formulas with same formula index
			fillFormulaCPF(gf, node.getCPF());
		}
		
		groundBN.show();
	} 
	
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf) throws ConversionException {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		fillFormulaCPF(gf, cpf, 1, addr);
	}
	
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, int iNode, int[] addr) throws ConversionException {
		BeliefNode[] nodes = cpf.getDomainProduct();
		State state = bln.getState();
		if(iNode == nodes.length) {
			double value = gf.isTrue(state) ? 1 : 0;
			addr[0] = 0;
			cpf.put(addr, new ValueDouble(value));
			addr[0] = 1;
			cpf.put(addr, new ValueDouble(1.0-value));
			return;
		}
		Discrete domain = (Discrete)nodes[iNode].getDomain();
		BeliefNode node = nodes[iNode];
		for(int i = 0; i < domain.getOrder(); i++) {
			addr[iNode] = i;
			// TODO consider non-boolean parent nodes
			if(i == 0)
				state.set(node.getName(), true);
			else
				state.set(node.getName(), false);
			fillFormulaCPF(gf, cpf, iNode+1, addr);
		}
	}
	
	public static void main(String[] args) {
		try { 
			BayesianLogicNetwork bln = new BayesianLogicNetwork(new BLOGModel("relxy.blog", "relxy.xml"), "relxy.mln");
			new GroundBLN(bln, "relxy.blogdb");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
