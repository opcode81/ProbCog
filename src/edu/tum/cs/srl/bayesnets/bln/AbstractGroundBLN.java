package edu.tum.cs.srl.bayesnets.bln;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.ParameterGrounder;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.DecisionNode;
import edu.tum.cs.srl.bayesnets.ExtendedNode;
import edu.tum.cs.srl.bayesnets.ParentGrounder;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.tools.Pair;
import edu.tum.cs.tools.Stopwatch;
import edu.tum.cs.tools.StringTool;

public abstract class AbstractGroundBLN {
	/**
	 * the ground Bayesian network (or ground auxiliary Bayesian network)
	 */
	protected BeliefNetworkEx groundBN;
	/**
	 * the underlying template model
	 */
	protected AbstractBayesianLogicNetwork bln;
	/**
	 * list of auxiliary node names contained in the ground Bayesian network (null if the network is not an auxiliary network)
	 */
	protected Vector<String> hardFormulaNodes;
	/**
	 * the file from which the evidence database was loaded (if any)
	 */
	protected String databaseFile;
	/**
	 * the database for which the ground model was instantiated
	 */
	protected Database db;
	/**
	 * temporary mapping of function names to relational nodes that can serve for instantiation (used only during grounding)
	 */
	protected HashMap<String, Vector<RelationalNode>> functionTemplates;
	/**
	 * temporary storage of names of instantiated variables (to avoid duplicate instantiation during grounding)
	 */
	protected HashSet<String> instantiatedVariables;
	protected HashMap<String, Value[]> subCPFCache;
	protected boolean debug = false;	
	/**
	 * maps an instantiated ground node to a string identifying the CPF template that was used to create it
	 */
	protected HashMap<BeliefNode, String> cpfIDs;
	
	public AbstractGroundBLN(AbstractBayesianLogicNetwork bln, Database db) {
		init(bln, db);
	}
	
	public AbstractGroundBLN(AbstractBayesianLogicNetwork bln, String databaseFile) throws Exception {
		this.databaseFile = databaseFile;
		System.out.println("reading evidence...");
		Database db = new Database(bln.rbn);
		db.readBLOGDB(databaseFile, true);
		init(bln, db);
	}
	
	protected void init(AbstractBayesianLogicNetwork bln, Database db) {
		this.bln = bln;
		this.db = db;		
		cpfIDs = new HashMap<BeliefNode, String>();
	}

	/**
	 * instantiates the auxiliary Bayesian network for this model
	 * @throws Exception 
	 */
	public void instantiateGroundNetwork() throws Exception {
		instantiateGroundNetwork(true);
	}
	
	/**
	 * instantiates the ground Bayesian network for this model
	 * @param addAuxiliaryVars if true, also adds auxiliary nodes to the network that correspond to the hard logical constraints
	 * @throws Exception
	 */
	public void instantiateGroundNetwork(boolean addAuxiliaryVars) throws Exception {
		Stopwatch sw = new Stopwatch();
		sw.start();
		
		System.out.println("generating network...");
		groundBN = new BeliefNetworkEx();
		
		// ground regular probabilistic nodes (i.e. ground atoms)
		System.out.println("  regular nodes");
		RelationalBeliefNetwork rbn = bln.rbn;
		
		// get an ordering of the functions in which to ground atoms - based on the topological ordering of the nodes in the RBN
		// and collect the RelationalNodes that can be used as templates to ground variables for the various functions
		int[] order = rbn.getTopologicalOrder();
		HashSet<String> handledFunctions = new HashSet<String>();
		Vector<String> functionOrder = new Vector<String>();
		functionTemplates = new HashMap<String, Vector<RelationalNode>>();
		for(int i = 0; i < order.length; i++) {
			int nodeNo = order[i];
			ExtendedNode extNode = rbn.getExtendedNode(nodeNo);
			// determine if the node can be used to instantiate a variable
			if(!(extNode instanceof RelationalNode)) 
				continue;
			RelationalNode relNode = (RelationalNode)extNode;			
			if(!relNode.isFragment()) // nodes that do not correspond to fragments can be ignored
				continue;
			// keep track of handled functions and add to the list of functions
			String f = relNode.getFunctionName();
			if(!handledFunctions.contains(f)) {
				functionOrder.add(f);
				handledFunctions.add(f);
			}
			// remember that this node can be instantiated using this relational node
			Vector<RelationalNode> v = functionTemplates.get(f);
			if(v == null) {
				v = new Vector<RelationalNode>();
				functionTemplates.put(f, v);
			}
			v.add(relNode);
		}
		
		// go through all function names in order and generate all groundings for each of them
		instantiatedVariables = new HashSet<String>();
		subCPFCache = new HashMap<String, Value[]>();
		for(String functionName : functionOrder) {
			System.out.println("    " + functionName);
			Collection<String[]> parameterSets = ParameterGrounder.generateGroundings(bln.rbn, functionName, db);
			for(String[] params : parameterSets) 
				instantiateVariable(functionName, params);
		}
		
		// clean up
		instantiatedVariables = null;
		functionTemplates = null;
		subCPFCache = null;
		
		// add auxiliary variables for formulaic constraints
		if(addAuxiliaryVars) {
			System.out.println("  formulaic nodes");
			hardFormulaNodes = new Vector<String>();
			groundFormulaicNodes();
		}
		
		System.out.println("network size: " + getGroundNetwork().bn.getNodes().length + " nodes");
		System.out.println(String.format("construction time: %.4fs", sw.getElapsedTimeSecs()));
	}
	
	/**
	 * instantiates the variable that corresponds to the given function name and actual parameters
	 * by looking for a template and applying it, or simply returns the variable if it was previously instantiated
	 * @param functionName
	 * @param params
	 * @throws Exception
	 */
	protected BeliefNode instantiateVariable(String functionName, String[] params) throws Exception {
		// check if the variable was previously instantiated and return the node if so
		String varName = Signature.formatVarName(functionName, params);
		if(instantiatedVariables.contains(varName))
			return groundBN.getNode(varName);
		
		int suitableTemplates = 0;
		BeliefNode ret = null;
		
		// consider all the relational nodes that could be used to instantiate the variable
		Vector<RelationalNode> templates = functionTemplates.get(functionName);
		if(templates == null)
			throw new Exception("No templates from which " + Signature.formatVarName(functionName, params) + " could be constructed.");
		for(RelationalNode relNode : templates) {
			
			// if the node is subject to preconditions (decision node parents), check if they are met
			boolean preconditionsMet = true;
			for(DecisionNode decision : relNode.getDecisionParents()) {					
				if(!decision.isTrue(relNode.params, params, db, false)) {
					preconditionsMet = false;
					break;
				}
			}
			if(!preconditionsMet)
				continue;
			
			suitableTemplates++;
			if(suitableTemplates > 1)
				throw new Exception("More than one relational node could serve as the template for the variable " + varName);
			
			ret = instantiateVariable(relNode, params);
		}
		
		if(suitableTemplates == 0) {
			if(!this.bln.rbn.isEvidenceFunction(functionName))			
				throw new Exception("No relational node was found that could serve as the template for the variable " + varName);
			else { // if it's an evidence node, we don't need a template but add a detached dummy node that has a single 1.0 entry for its evidence value
				/*
				String value = this.db.getVariableValue(varName, true);
				Domain dom = new Discrete(new String[]{value});
				BeliefNode node = groundBN.addNode(varName, dom);
				CPF cpf = new CPF(new BeliefNode[]{node});
				cpf.setValues(new Value[]{new ValueDouble(1.0)});
				node.setCPF(cpf);
				ret = node;
				// TODO can't call this because we don't have a relNode; Actually we wouldn't want to have this node at all
				//onAddGroundAtomNode(relNode, actualParams, mainNode);
				 */				
				if(debug)
					System.out.println("      " + varName + " (skipped, is evidence)");
			}			
	    }

		return ret;
	}
	
	/**
	 * instantiates a variable from the given node template for the actual parameters
	 * @param relNode		the node that is to serve as the template
	 * @param actualParams	actual parameters
	 * @return
	 * @throws Exception
	 */
	protected BeliefNode instantiateVariable(RelationalNode relNode, String[] actualParams) throws Exception {
		// keep track of instantiated variables
		String mainNodeName = relNode.getVariableName(actualParams);
		instantiatedVariables.add(mainNodeName);
		if(debug) 
			System.out.println("      " + mainNodeName);

		// add the node itself to the network				
		BeliefNode mainNode = groundBN.addNode(mainNodeName, relNode.node.getDomain());
		onAddGroundAtomNode(relNode, actualParams, mainNode);

		// add edges from the parents
		ParentGrounder pg = bln.rbn.getParentGrounder(relNode);
		Vector<Map<Integer, String[]>> groundings = pg.getGroundings(actualParams, db);
		// - normal case: just CPF application for one set of parents
		if(!relNode.hasAggregator()) {
			if(groundings.size() != 1) 
				throw new Exception("Cannot instantiate " + mainNodeName + " for " + groundings.size() + " groups of parents.");			
			if(debug) {
				System.out.println("        relevant nodes/parents from " + pg.toString());
				Map<Integer, String[]> grounding = groundings.firstElement();						
				for(Entry<Integer, String[]> e : grounding.entrySet()) {							
					System.out.println("          " + bln.rbn.getRelationalNode(e.getKey()).getVariableName(e.getValue()));
				}
			}
			instantiateCPF(groundings.firstElement(), relNode, mainNode);
		}				
		// - other case: use combination function
		else { 
			// determine if auxiliary nodes need to be used and connect the parents appropriately			
			if(relNode.aggregator.charAt(0) != '=') {
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
			}
			// if the node is functionally determined by the parents, aux. nodes carrying the CPD in the template node are not required
			// we link the grounded parents directly
			else {
				ArrayList<BeliefNode> domprod = new ArrayList<BeliefNode>(); // vector ordered by parent set (i.e. the parents belonging to a set are grouped)
				domprod.add(mainNode);
				for(Map<Integer, String[]> grounding : groundings) {
					HashMap<BeliefNode,BeliefNode> src2targetParent = new HashMap<BeliefNode,BeliefNode>();
					connectParents(grounding, relNode, mainNode, src2targetParent, null);
					domprod.addAll(src2targetParent.values());
				}
				// force parent ordering (ordered by group) in CPF
				mainNode.getCPF().buildZero(domprod.toArray(new BeliefNode[domprod.size()]), false);
			}
			// apply combination function
			String combFunc = relNode.aggregator;
			CPFFiller filler;
			if(combFunc == null || combFunc.equals("=OR") || combFunc.equals("OR")) {
				// check if the domain is really boolean
				if(!RelationalBeliefNetwork.isBooleanDomain(mainNode.getDomain()))
					throw new Exception("Cannot use OR aggregator on non-Boolean node " + relNode.toString());
				// set filler
				filler = new CPFFiller_ORGrouped(mainNode, groundings.firstElement().size()-1);
			}
			else
				throw new Exception("Cannot ground structure because of multiple parent sets for node " + mainNodeName + " with unhandled aggregator " + relNode.aggregator);
			filler.fill();
			cpfIDs.put(mainNode, combFunc);
		}
		
		return mainNode;
	}
	
	protected void init() {}
	
	protected abstract void groundFormulaicNodes() throws Exception;
	
	protected abstract void onAddGroundAtomNode(RelationalNode relNode, String[] params, BeliefNode instance);
	
	/**
	 * adds a node corresponding to a hard constraint to the network - along with the necessary edges
	 * @param nodeName  	name of the node to add for the constraint
	 * @param parentGAs		collection of names of parent nodes/ground atoms 
	 * @return a pair containing the node added and the array of parent nodes
	 * @throws Exception
	 */
	public Pair<BeliefNode, BeliefNode[]> addHardFormulaNode(String nodeName, Collection<String> parentGAs) throws Exception {
		hardFormulaNodes.add(nodeName);
		BeliefNode node = groundBN.addNode(nodeName);		
		BeliefNode[] parents = new BeliefNode[parentGAs.size()];
		int i = 0;
		for(String strGA : parentGAs) {
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
		return new Pair<BeliefNode, BeliefNode[]>(node, parents);
	}
	
	public Database getDatabase() {
		return db;
	}
	
	/**
	 * connects the parents given by the grounding to the target node 
	 * @param parentGrounding
	 * @param srcRelNode  the relational node that is to serve as the template for the target node
	 * @param targetNode  the node in the ground network to connect the parents to
	 * @param src2targetParent  a mapping in which to store which node in the template model produced which instantiated parent in the ground network (or null)
	 * @param constantSettings  a mapping in which to store bindings of constants (or null)
	 * @throws Exception 
	 */
	protected void connectParents(Map<Integer, String[]> parentGrounding, RelationalNode srcRelNode, BeliefNode targetNode, HashMap<BeliefNode, BeliefNode> src2targetParent, HashMap<BeliefNode, Integer> constantSettings) throws Exception {
		for(Entry<Integer, String[]> entry : parentGrounding.entrySet()) {
			RelationalNode relParent = bln.rbn.getRelationalNode(entry.getKey());
			if(relParent == srcRelNode)
				continue;
			if(relParent.isConstant) {
				//System.out.println("Constant node: " + parent.getName() + " = " + entry.getValue()[0]);
				if(constantSettings != null) 
					constantSettings.put(relParent.node, ((Discrete)relParent.node.getDomain()).findName(entry.getValue()[0]));
				continue;
			}
			if(relParent.isPrecondition) {
				if(constantSettings != null)
					constantSettings.put(relParent.node, 0); // precondition nodes are always true
				continue;
			}
			BeliefNode parent = instantiateVariable(relParent.getFunctionName(), entry.getValue());
			//System.out.println("Connecting " + parent.getName() + " to " + targetNode.getName());
			groundBN.bn.connect(parent, targetNode);
			if(src2targetParent != null) src2targetParent.put(relParent.node, parent);
		}
	}
	
	/**
	 * connects the parents given by the grounding to the target node and transfers the (correct part of the) CPF to the target node
	 * @param parentGrounding  a grounding (mapping of indices of relational nodes to an array of actual parameters)
	 * @param srcRelNode  relational node that the CPF is to be copied from 
	 * @param targetNode  the target node to connect parents to and whose CPF is to be written
	 * @throws Exception
	 */
	protected void instantiateCPF(Map<Integer, String[]> parentGrounding, RelationalNode srcRelNode, BeliefNode targetNode) throws Exception {
		// connect parents, determine domain products, and set constant nodes (e.g. "x") to their respective constant value
		HashMap<BeliefNode, BeliefNode> src2targetParent = new HashMap<BeliefNode, BeliefNode>();
		HashMap<BeliefNode, Integer> constantSettings = new HashMap<BeliefNode, Integer>();
		connectParents(parentGrounding, srcRelNode, targetNode, src2targetParent, constantSettings);
		
		// set decision nodes as constantly true
		BeliefNode[] srcDomainProd = srcRelNode.node.getCPF().getDomainProduct();
		for(int i = 1; i < srcDomainProd.length; i++) {
			if(srcDomainProd[i].getType() == BeliefNode.NODE_DECISION)
				constantSettings.put(srcDomainProd[i], 0); // 0 = True
		}
		
		// establish the correct domain product order (which must reflect the order in the source node)
		CPF targetCPF = targetNode.getCPF();
		BeliefNode[] targetDomainProd = targetCPF.getDomainProduct();
		int j = 1;
		HashSet<BeliefNode> handledParents = new HashSet<BeliefNode>();
		for(int i = 1; i < srcDomainProd.length; i++) {			
			BeliefNode targetParent = src2targetParent.get(srcDomainProd[i]);
			//System.out.println("Parent corresponding to " + srcDomainProd[i].getName() + " is " + targetParent);			
			if(targetParent != null) {
				if(handledParents.contains(targetParent))
					continue;
				if(j >= targetDomainProd.length)
					throw new Exception("Domain product of " + targetNode + " too small; size = " + targetDomainProd.length + "; tried to add " + targetParent + "; already added " + StringTool.join(",", targetDomainProd));
				targetDomainProd[j++] = targetParent;
				handledParents.add(targetParent);
			}
		}
		if(j != targetDomainProd.length)
			throw new Exception("CPF domain product not fully filled: handled " + j + ", needed " + targetDomainProd.length);
		targetCPF.buildZero(targetDomainProd, false);
		
		// transfer the CPF values
		String cpfID = Integer.toString(srcRelNode.index);
		// - if the original relational node had exactly the same number of parents as the instance, 
		//   we can safely transfer its CPT to the instantiated node
		if(srcDomainProd.length == targetDomainProd.length) {			
			targetCPF.setValues(srcRelNode.node.getCPF().getValues());
		}
		// - otherwise we must extract the relevant columns that apply to the constant setting
		else {
			Value[] subCPF;						
			// get the subpart from the cache if possible
			cpfID += constantSettings.toString(); 
			subCPF = subCPFCache.get(cpfID);
			if(subCPF == null) {
				subCPF = getSubCPFValues(srcRelNode.node.getCPF(), constantSettings);
				subCPFCache.put(cpfID, subCPF);
			}
			
			targetCPF.setValues(subCPF);
		}		
		cpfIDs.put(targetNode, cpfID);
		
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
		// if we have the setting of the i-th node, use it
		Integer setting = constantSettings.get(n);
		if(setting != null) {
			addr[i] = setting;
			getSubCPFValues(cpf, constantSettings, i+1, addr, ret);
		}
		// otherwise consider all possible settings
		else {
			Domain d = domProd[i].getDomain();		
			for(int j = 0; j < d.getOrder(); j++) {
				addr[i] = j;
				getSubCPFValues(cpf, constantSettings, i+1, addr, ret);
			}
		}
	}
	
	/**
	 * abstract base class for filling a CPF that is determined by a combination function
	 * @author jain
	 */
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
	
	/**
	 * CPF filler for simple OR of boolean nodes
	 * @author jain
	 */
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
	
	/**
	 * CPF filler for disjunction of conjunction of boolean nodes
	 * @author jain
	 */
	public class CPFFiller_ORGrouped extends CPFFiller {
		int groupSize;

		/**
		 * 
		 * @param node node whose CPF to fill
		 * @param groupSize number of consecutive parents that make up a group representing a conjunction
		 */
		public CPFFiller_ORGrouped(BeliefNode node, int groupSize) {
			super(node);
			this.groupSize = groupSize;
		}

		@Override
		protected double getValue(int[] addr) {
			// disjunction of conjunction of boolean nodes (each conjunction is of groupSize)
			// order in boolean domains is 0=True, 1=False
			boolean isTrue = false;
			int g = 0;
			for(int i = 1; i < addr.length;) {
				if((i-1) % groupSize == 0) {
					if(isTrue) 
						break;
				}
				isTrue = addr[i] == 0;
				if(!isTrue) { // skip to next conjunction
					++g;
					i = 1 + g * groupSize;
					continue;
				}
				++i;
			}
			return (addr[0] == 0 && isTrue) || (addr[0] == 1 && !isTrue) ? 1.0 : 0.0;
		}
	}
	
	public void show() {
		groundBN.show();
	}
	
	/**
	 * adds to the given evidence the evidence that is implied by the hard formulaic constraints (since all of them must be true)
	 * @param evidence 
	 * @return a list of domain indices for each node in the network (-1 for no evidence)
	 */
	public int[] getFullEvidence(String[][] evidence) {
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
	
	public BeliefNetworkEx getGroundNetwork() {
		return this.groundBN;
	}
	
	/**
	 * gets the unique identifier of the CPF that is associated with the given ground node of the network
	 * @param node
	 * @return
	 */
	public String getCPFID(BeliefNode node) {
		String cpfID = cpfIDs.get(node);
		return cpfID;
	}
	
	public void setDebugMode(boolean enabled) {
		this.debug = enabled;
	}
}
