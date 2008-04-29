package edu.tum.cs.bayesnets.relational.inference;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.DiscreteEvidence;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.ksu.cis.bnj.ver3.core.Value;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.ksu.cis.bnj.ver3.inference.approximate.sampling.AIS;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.relational.core.*;
import edu.tum.cs.bayesnets.relational.core.bln.BayesianLogicNetwork;
import edu.tum.cs.bayesnets.relational.core.bln.GroundFormula;
import edu.tum.cs.bayesnets.relational.core.bln.State;
import edu.tum.cs.bayesnets.relational.learning.Database;
import edu.tum.cs.tools.Stopwatch;

public class GroundBLN {
	protected BeliefNetworkEx groundBN;
	protected BayesianLogicNetwork bln;
	protected Vector<String> hardFormulaNodes;
	protected Database db;
	
	public GroundBLN(BayesianLogicNetwork bln, String databaseFile) throws Exception {
		this.bln = bln;
		
		System.out.println("reading evidence...");
		db = new Database(bln.rbn);
		db.readBLOGDB(databaseFile);
		
		System.out.println("generating network...");
		groundBN = new BeliefNetworkEx();
		
		// ground regular probabilistic nodes (i.e. ground atoms)
		System.out.println("  regular nodes");
		RelationalBeliefNetwork rbn = bln.rbn;
		int[] order = rbn.getTopologicalOrder();
		for(int i = 0; i < order.length; i++) {
			int nodeNo = order[i];
			RelationalNode relNode = rbn.getRelationalNode(nodeNo);
			System.out.println("    " + relNode);
			if(relNode.isConstant)
				continue;
			Collection<String[]> parameterSets = ParameterGrounder.generateGroundings(relNode, db);
			for(String[] params : parameterSets) {
				
				// add the node itself to the network
				String mainNodeName = relNode.getVariableName(params);
				BeliefNode mainNode = groundBN.addNode(mainNodeName, relNode.node.getDomain());

				// add edges from the parents
				ParentGrounder pg = rbn.getParentGrounder(relNode);
				Vector<Map<Integer, String[]>> groundings = pg.getGroundings(params, db);
				// - normal case: just one set of parents
				if(groundings.size() == 1) { 
					instantiateCPF(groundings.firstElement(), relNode, mainNode);
				}				
				// - several sets of parents -> use combination function
				else { 
					// create auxiliary nodes, one for each set of parents
					Vector<BeliefNode> auxNodes = new Vector<BeliefNode>();
					int k = 0; 
					for(Map<Integer, String[]> grounding : groundings) {
						// create auxiliary node
						String auxNodeName = String.format("AUX%d_%s", k++, mainNode.getName());
						BeliefNode auxNode = groundBN.addNode(auxNodeName, mainNode.getDomain());
						auxNodes.add(auxNode);
						// create links from parents to auxiliary node and transfer CPF
						instantiateCPF(grounding, relNode, auxNode);
					}
					// connect auxiliary nodes to main node
					for(BeliefNode parent : auxNodes) {
						//System.out.printf("connecting %s and %s\n", parent.getName(), mainNode.getName());
						groundBN.bn.connect(parent, mainNode);
					}
					// apply combination function
					String combFunc = relNode.aggregator;
					CPFFiller filler;
					if(combFunc == null || combFunc.equals("OR")) {
						// check if the domain is really boolean
						if(!RelationalBeliefNetwork.isBooleanDomain(mainNode.getDomain()))
							throw new Exception("Cannot use OR aggregator on non-Boolean node " + relNode.toString());
						// set filler
						filler = new CPFFiller_OR(mainNode);
					}
					else
						throw new Exception("Cannot ground structure because of multiple parent sets for node " + mainNodeName + " with unhandled aggregator " + relNode.aggregator);
					filler.fill();
				}
			}
		}
		
		// ground formulaic nodes
		System.out.println("  formulaic nodes");
		hardFormulaNodes = new Vector<String>();
		bln.generateGroundFormulas(databaseFile);
		for(GroundFormula gf : bln.iterGroundFormulas()) {
			Vector<String> vGA = gf.getGroundAtoms();
			// create a node for the ground formula
			String nodeName = "GF" + gf.idxGF;
			hardFormulaNodes.add(nodeName);
			BeliefNode node = groundBN.addNode(nodeName);			
			// add edges from ground atoms
			Vector<String> GAs = gf.getGroundAtoms();
			BeliefNode[] parents = new BeliefNode[GAs.size()];
			int i = 0;
			for(String strGA : GAs) {
				BeliefNode parent = groundBN.getNode(strGA);
				if(parent == null) { // if the atom cannot be found, e.g. attr(X,Value), it might be a functional, so remove the last argument and try again, e.g. attr(X) (=Value)
					String parentName = strGA.substring(0, strGA.lastIndexOf(",")) + ")";
					parent = groundBN.getNode(parentName);
					if(parent == null)
						throw new Exception("Could not find node for ground atom " + strGA);
				}
				groundBN.bn.connect(parent, node);
				parents[i++] = parent;
			}
			// fill CPT according to formula semantics
			// TODO try to reuse CPFs generated for previous formulas with same formula index
			fillFormulaCPF(gf, node.getCPF(), parents, GAs);
		}
	}
	
	/**
	 * connects the parents given by the grounding to the target node and transfers the (correct part of the) CPF to the target node
	 * @param parentGrounding  a grounding
	 * @param srcRelNode  relational node that the CPF is to be copied from 
	 * @param targetNode  the target node to connect parents to and whose CPF is to be written
	 * @throws Exception
	 */
	public void instantiateCPF(Map<Integer, String[]> parentGrounding, RelationalNode srcRelNode, BeliefNode targetNode) throws Exception {
		// connect parents and determine domain products
		HashMap<BeliefNode, BeliefNode> src2targetParent = new HashMap<BeliefNode, BeliefNode>();
		HashMap<BeliefNode, Integer> constantSettings = new HashMap<BeliefNode, Integer>();
		for(Entry<Integer, String[]> entry : parentGrounding.entrySet()) {
			RelationalNode relParent = bln.rbn.getRelationalNode(entry.getKey());
			if(relParent == srcRelNode)
				continue;
			if(relParent.isConstant) {
				//System.out.println("Constant node: " + parent.getName() + " = " + entry.getValue()[0]);
				constantSettings.put(relParent.node, ((Discrete)relParent.node.getDomain()).findName(entry.getValue()[0]));
				continue;
			}
			BeliefNode parent = groundBN.getNode(relParent.getVariableName(entry.getValue()));
			//System.out.println("Connecting " + parent.getName() + " to " + targetNode.getName());
			groundBN.bn.connect(parent, targetNode);
			src2targetParent.put(relParent.node, parent);
		}
		
		// establish the correct domain product order (which must reflect the order in the source node)	
		BeliefNode[] srcDomainProd = srcRelNode.node.getCPF().getDomainProduct();
		CPF targetCPF = targetNode.getCPF();
		BeliefNode[] targetDomainProd = targetCPF.getDomainProduct();
		int j = 1;
		for(int i = 1; i < srcDomainProd.length; i++) {			
			BeliefNode targetParent = src2targetParent.get(srcDomainProd[i]);
			//System.out.println("Parent corresponding to " + srcDomainProd[i].getName() + " is " + targetParent);
			if(targetParent != null) {
				targetDomainProd[j++] = targetParent;
			}
		}
		if(j != targetDomainProd.length)
			throw new Exception("CPF domain product not fully filled: handled " + j + ", needed " + targetDomainProd.length);
		
		// transfer the CPF values
		if(srcDomainProd.length == targetDomainProd.length)
			targetCPF.setValues(srcRelNode.node.getCPF().getValues());
		else
			targetCPF.setValues(getSubCPFValues(srcRelNode.node.getCPF(), constantSettings));
		
		/*
		// print domain products (just to check)
		BeliefNode n = srcRelNode.node;
		System.out.println("\nsrc:");
		BeliefNode[] domProd = n.getCPF().getDomainProduct();
		for(int i = 0; i < domProd.length; i++) {
			System.out.println("  " + domProd[i].getName());
		}
		System.out.println("target:");
		n = targetNode;
		domProd = n.getCPF().getDomainProduct();
		for(int i = 0; i < domProd.length; i++) {
			System.out.println("  " + domProd[i].getName());
		}
		System.out.println();
		*/
	}
	
	protected Value[] getSubCPFValues(CPF cpf, HashMap<BeliefNode, Integer> constantSettings) {
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		Vector<Value> v = new Vector<Value>();
		getSubCPFValues(cpf, constantSettings, 0, addr, v);
		return v.toArray(new Value[0]);
	}
	
	protected void getSubCPFValues(CPF cpf, HashMap<BeliefNode, Integer> constantSettings, int i, int[] addr, Vector<Value> ret) {
		BeliefNode[] domProd = cpf.getDomainProduct();
		if(i == domProd.length) {
			ret.add(cpf.get(addr));			
			return;
		}
		BeliefNode n = domProd[i];
		Integer setting = constantSettings.get(n);
		if(setting != null) {
			addr[i] = setting;
			getSubCPFValues(cpf, constantSettings, i+1, addr, ret);
		}
		else {
			Domain d = domProd[i].getDomain();		
			for(int j = 0; j < d.getOrder(); j++) {
				addr[i] = j;
				getSubCPFValues(cpf, constantSettings, i+1, addr, ret);
			}
		}
	}
	
	public abstract class CPFFiller {
		CPF cpf;
		BeliefNode[] nodes;
		
		public CPFFiller(BeliefNode node) {
			cpf = node.getCPF();
			nodes = cpf.getDomainProduct();
		}
		
		public void fill() throws Exception {
			int[] addr = new int[nodes.length];
			fill(0, addr);
		}
		
		protected void fill(int iNode, int[] addr) throws Exception {
			// if all parents have been set, determine the truth value of the formula and 
			// fill the corresponding entry of the CPT 
			if(iNode == nodes.length) {
				cpf.put(addr, new ValueDouble(getValue(addr)));				
				return;
			}
			Discrete domain = (Discrete)nodes[iNode].getDomain();
			// - recursively consider all settings
			for(int i = 0; i < domain.getOrder(); i++) {
				// set address 
				addr[iNode] = i;
				// recurse
				fill(iNode+1, addr);
			}
		}
		
		protected abstract double getValue(int[] addr);
	}
	
	public class CPFFiller_OR extends CPFFiller {
		public CPFFiller_OR(BeliefNode node) {
			super(node);
		}

		@Override
		protected double getValue(int[] addr) {
			// OR of boolean nodes: if one of the nodes is true (0), it is true
			boolean isTrue = false;
			for(int i = 1; i < addr.length; i++)
				isTrue = isTrue || addr[i] == 0;
			return (addr[0] == 0 && isTrue) || (addr[0] == 1 && !isTrue) ? 1.0 : 0.0;
		}
	}
	
	
	/*protected void transferCPF(RelationalNode source, BeliefNode target) {
		// TODO this might fail because of incorrect ordering of parents
		target.getCPF().setValues(source.node.getCPF().getValues());	
	}*/
	
	public void show() {
		groundBN.show();
	}
	
	/**
	 * fills the CPF of a formulaic node
	 * @param gf	the ground formula to evaluate for all possible settings
	 * @param cpf	the CPF of the formulaic node to fill
	 * @param parents	the parents of the formulaic node
	 * @param parentGAs	the ground atom string names of the parents (in case the node names do not match them)
	 * @throws Exception
	 */
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		fillFormulaCPF(gf, cpf, parents, parentGAs, 0, addr);
	}
	
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs, int iParent, int[] addr) throws Exception {
		// if all parents have been set, determine the truth value of the formula and 
		// fill the corresponding column of the CPT 
		State state = bln.getState();
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
	 * adds to the given evidence the evidence that is implied by the hard formulaic constraints
	 * @param evidence 
	 * @return a list of domain indices for each node in the network (-1 for no evidence)
	 */
	protected int[] getFullEvidence(String[][] evidence) {
		String[][] fullEvidence = new String[evidence.length+this.hardFormulaNodes.size()][2];
		for(int i = 0; i < evidence.length; i++) {
			fullEvidence[i][0] = evidence[i][0];
			fullEvidence[i][1] = evidence[i][1];
		}
		{
			int i = evidence.length;
			for(String node : hardFormulaNodes) {
				fullEvidence[i][0] = node;
				fullEvidence[i][1] = "True";
				i++;
			}
		}
		return groundBN.evidence2DomainIndices(fullEvidence);
	}
	
	public void inferAIS(int numSamples) {
		boolean useEvidence = true;
		if(useEvidence) {
			BeliefNode[] nodes = groundBN.bn.getNodes();
			int[] evidenceDomainIndices = getFullEvidence(db.getEntriesAsArray());
			for(int i = 0; i < evidenceDomainIndices.length; i++)
				if(evidenceDomainIndices[i] != -1) {
					nodes[i].setEvidence(new DiscreteEvidence(evidenceDomainIndices[i]));
				}
		}
		
		AIS ais = new AIS();
		ais.setNumSamples(numSamples);
		ais.setInterval(50);
		ais.run(groundBN.bn);		
	}
	
	public BeliefNetworkEx getGroundNetwork() {
		return this.groundBN;
	}
	
	public static void main(String[] args) {
		try { 
			int test = 1;
			
			if(test == 0) {
				String dir = "/usr/wiss/jain/work/code/SRLDB/bln/test/";
				BayesianLogicNetwork bln = new BayesianLogicNetwork(new BLOGModel(dir + "relxy.blog", dir + "relxy.xml"), dir + "relxy.bln");
				GroundBLN gbln = new GroundBLN(bln, dir + "relxy.blogdb");
				Stopwatch sw = new Stopwatch();
				sw.start();
				new LikelihoodWeighting(gbln).infer(new String[]{"rel(X,Y)"}, 1000, 100);
				//gbln.inferAIS(new String[][]{{"prop1(X)", "A1"},{"prop2(Y)", "A1"}}, 1000);
				sw.stop();
				System.out.println("Inference time: " + sw.getElapsedTimeSecs() + " seconds");
			}
			if(test == 1) {
				String dir = "/usr/wiss/jain/work/code/SRLDB/blog/kitchen/meal_goods2/";
				BayesianLogicNetwork bln = new BayesianLogicNetwork(new BLOGModel(dir + "meals_any_names.blog", dir + "meals_any.learnt.xml"), dir + "meals_any.bln");
				GroundBLN gbln = new GroundBLN(bln, dir + "query2.blogdb");
				gbln.show();
				//gbln.infer(new String[][]{{"prop1(X)", "A1"},{"prop2(Y)", "A1"}}, new String[]{"rel(x,y)"}, 1000, 100);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
