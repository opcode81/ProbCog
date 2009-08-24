package edu.tum.cs.srl.bayesnets.learning;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.DecisionNode;
import edu.tum.cs.srl.bayesnets.ExtendedNode;
import edu.tum.cs.srl.bayesnets.ParentGrounder;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.tools.StringTool;

public class CPTLearner extends edu.tum.cs.bayesnets.learning.CPTLearner {
	
	protected HashMap<Integer, HashMap<String, Integer>> marginals;
	protected int numExamples;
	protected boolean verbose;
	protected boolean debug = false;
	
	public CPTLearner(RelationalBeliefNetwork bn) throws Exception {
		this(bn, false, false);
	}
	
	public CPTLearner(RelationalBeliefNetwork bn, boolean uniformDefault, boolean debug) throws Exception {
		super(bn);	
		setUniformDefault(uniformDefault);
		this.debug = debug;
		//marginals = new HashMap<Integer, HashMap<String,Integer>>(); // just for debugging
	}
	
	/**
	 * count an example (variable setting) by incrementing the counter for the given variable
	 * @param db			the database containing propositions
	 * @param node			node of the variable for which we are counting an example
	 * @param params		the node's actual parameters
	 * @param closedWorld	whether the closed-world assumption is to be made
	 * @throws Exception
	 */
	protected void countVariable(Database db, RelationalNode node, String[] params, boolean closedWorld) throws Exception {
		// if the node is not CPT-based, skip it
		if(!node.hasCPT())
			return;
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		// get the node and its associated counter
		ExampleCounter counter = this.counters[node.index];
		// get the main variable's name
		String varName = Signature.formatVarName(node.getFunctionName(), params);
		//System.out.println("counting " + varName);
		
		// obtain all groundings of the relevant variables
		ParentGrounder pg = bn.getParentGrounder(node);
		Vector<Map<Integer, String[]>> groundings = pg.getGroundings(params, db);
		if(groundings == null) {
			System.err.println("Variable " + Signature.formatVarName(node.getFunctionName(), params)+ " skipped because parents could not be grounded.");
			return;
		}

		//System.out.println();
		/*HashMap<String, Integer> counts = marginals.get(node.index);
		if(counts == null) {
			counts = new HashMap<String, Integer>();
			marginals.put(node.index, counts);
		}*/
		
		double exampleWeight = 1.0;

		// do some precomputations to determine example weight
		if(false) {
			// TODO the code in this block does not yet consider the possibility of decision nodes as parents
			// - for average of conditional probabilities compute the homogeneity of the relational parents to obtain suitable example weights		
			if(node.aggregator != null && node.aggregator.equals("AVG") && node.parentMode != null && node.parentMode.equals("CP")) {
				// create a vector of counts/probabilities
				// first get the number of configurations that are possible for each parent
				int dim = 1;
				Vector<Integer> relevantParentIndices = new Vector<Integer>();
				Vector<Integer> precondParentIndices = new Vector<Integer>();
				for(RelationalNode parent : bn.getRelationalParents(node)) {
					if(parent.isPrecondition) {
						precondParentIndices.add(parent.index);
						continue;
					}
					dim *= parent.getDomain().getOrder();
					relevantParentIndices.add(parent.index);
				}
				double[] v = new double[dim];
				// gather counts
				int numExamples = 0;
				for(Map<Integer, String[]> paramSets : groundings) { // for each grounding...
					boolean skip = false;
					// check if the preconditions are met
					for(Integer nodeIdx : precondParentIndices) {
						RelationalNode ndCurrent = bn.getRelationalNode(nodeIdx);
						String value = db.getVariableValue(ndCurrent.getVariableName(paramSets.get(ndCurrent.index)), closedWorld);
						if(!value.equalsIgnoreCase("true")) {
							skip = true;
							break;
						}
					}
					if(skip) 
						continue;
					// count the example
					int factor = 1;
					int addr = 0;
					for(Integer nodeIdx : relevantParentIndices) {
						RelationalNode ndCurrent = bn.getRelationalNode(nodeIdx);
						//String value = db.getVariableValue(ndCurrent.getVariableName(paramSets.get(ndCurrent.index)), closedWorld);
						String value = ndCurrent.getValueInDB(paramSets.get(ndCurrent.index), db, closedWorld);
						Discrete dom = ndCurrent.getDomain();
						int domIdx = dom.findName(value);					
						if(domIdx < 0) {
							String[] domain = BeliefNetworkEx.getDiscreteDomainAsArray(ndCurrent.node);
							throw new Exception("Could not find value '" + value + "' in domain of " + ndCurrent.toString() + " {" + StringTool.join(",", domain) + "}");
						}
						addr += factor * domIdx; 
						factor *= dom.getOrder();
					}
					v[addr] += 1;	
					numExamples++;
				}
				// obtain probabilities
				for(int i = 0; i < v.length; i++)
					v[i] = v[i] / numExamples;
				// calculate weight
				exampleWeight = 0;
				int exponent = 10;			
				for(int i = 0; i < v.length; i++) {
					exampleWeight += Math.pow(v[i], exponent);
				}
				//System.out.println("weight: " + exampleWeight);
			}
		}
		// precomputations done... now the actual counting starts
			
		// set the domain indices of all relevant nodes (node itself and parents)			
		for(Map<Integer, String[]> paramSets : groundings) { // for each grounding...
			// consider the concrete parents
			boolean countExample = true;
			int domainIndices[] = new int[this.nodes.length];
			for(int i = 0; i < counter.nodeIndices.length; i++) {
				int domain_idx = -1;
				ExtendedNode extCurrent = bn.getExtendedNode(counter.nodeIndices[i]);
				// decision node parents are always true, because we use them to define hard constraints on the use of the CPT we are learning;
				// whether the constraint that they represent is actually satisfied was checked beforehand
				if(extCurrent instanceof DecisionNode) {
					domain_idx = 0; // 0 = True 
				}
				// it's a regular parent
				else {
					// get the corresponding RelationalNode object
					RelationalNode ndCurrent = (RelationalNode)extCurrent;
					// determine the value of the node given the parameter settings implied by the main node
					String[] actualParams = paramSets.get(ndCurrent.index);
					if(actualParams == null) {
						Vector<String> availableNodes = new Vector<String>();
						for(Integer idx : paramSets.keySet())
							availableNodes.add(idx.toString() + "/" + ndCurrent.getNetwork().getRelationalNode(idx).toString());
						throw new Exception("Relevant node " + ndCurrent.index + "/" + ndCurrent + " has no grounding for main node instantiation " + varName + "; have only " + availableNodes.toString());
					}
					String value = ndCurrent.getValueInDB(actualParams, db, closedWorld);
					if(value == null)
						throw new Exception(String.format("Could not find setting for node named '%s' while processing '%s'", ndCurrent.getName(), varName));
					// if the node is a precondition, i.e. it is required to be true, check that it really is
					if(ndCurrent.isPrecondition && !value.equalsIgnoreCase("true")) {
						// it's not, so skip this example
						countExample = false;
						break;
					}
					// get the current node's domain and the index of its setting
					Discrete dom = (Discrete)(ndCurrent.node.getDomain());
					domain_idx = dom.findName(value);
					if(domain_idx == -1) {	
						String[] domElems = new String[dom.getOrder()];
						for(int j = 0; j < domElems.length; j++)
							domElems[j] = dom.getName(j);
						throw new Exception(String.format("'%s' not found in domain of %s {%s} while processing %s", value, ndCurrent.getFunctionName(), StringTool.join(",", domElems), varName));
					}					
					// side affair: learn the CPT of constant nodes here by incrementing the counter
					if(ndCurrent.isConstant) {
						int[] constantDomainIndices = new int[this.nodes.length];
						constantDomainIndices[ndCurrent.index] = domain_idx;
						this.counters[ndCurrent.index].count(constantDomainIndices);
					}
				}
				domainIndices[extCurrent.index] = domain_idx;
			}		
			
			// count this example
			if(countExample) {
				counter.count(domainIndices, exampleWeight);
				numExamples++;
				if(debug && verbose) { // just debug output
					StringBuffer condition = new StringBuffer();
					for(Entry<Integer, String[]> e : paramSets.entrySet()) {
						if(e.getKey() == node.index)
							continue;
						RelationalNode rn = bn.getRelationalNode(e.getKey());
						condition.append(' ');
						condition.append(rn.getVariableName(e.getValue()));
						condition.append('=');
						condition.append(rn.getDomain().getName(domainIndices[rn.index]));
					}
					System.out.println("    " + node.getVariableName(params) + "=" + node.getDomain().getName(domainIndices[node.index]) + " |" + condition);
				}
			}
			
			// keep track of counts (just debugging)
			/*String v = node.node.getDomain().getName(domainIndices[counter.nodeIndices[0]]);
			Integer i = counts.get(v);
			if(i == null)
				i = 0;
			counts.put(v, i+1);*/
		}
	}
	
	/**
	 * learn the CPTs from only the data that is given in the database (relations not in the database are not considered because the closed-world assumption is not being made)
	 * @param db
	 * @throws Exception
	 */
	@Deprecated
	public void learn(Database db) throws Exception {
		throw new Exception("No longer supported");
		/*for(Variable var : db.getEntries()) {
			countVariable(db, var.nodeName, var.params, false); // TODO: the node used is the one with the most parents that fits			
		}*/
	}

	/**
	 * generates for all nodes all the possible parameters (using the node signatures and domain elements from the database) and counts the corresponding examples 
	 * @param db
	 * @param closedWorld
	 * @param verbose
	 * @throws Exception
	 */
	public void learnTyped(Database db, boolean closedWorld, boolean verbose) throws Exception {		
		this.verbose = verbose;
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;		
		for(RelationalNode node : bn.getRelationalNodes()) { // for each node...
			if(node.isConstant || node.isBuiltInPred()) // ignore constant nodes as they do not correspond to logical atoms 
				continue;
			numExamples = 0;
			if(verbose)
				System.out.println("  " + node.getName());				
			// consider all possible bindings for the node's parameters and count
			String[] params = new String[node.params.length];			
			countVariable(db, node, params, bn.getSignature(node.getFunctionName()).argTypes, 0, closedWorld);
			if(verbose) 
				System.out.println("    " + numExamples + " counted");
			//System.out.println("    counts: " + marginals.get(node.index));
		}
	}
	
	/**
	 * generates all groundings (possible lists of parameters) of the node with the given name and counts the corresponding example
	 * @param db			the database (containing domains and propositions) to use
	 * @param nodeName		name of the node for which to count examples
	 * @param params	 	current list of parameters
	 * @param domainNames	list of domain names, with one entry for each parameter
	 * @param i				index into params at which to insert the next parameter 
	 * @param closedWorld	whether to make the closed-world assumption
	 * @throws Exception
	 */
	protected void countVariable(Database db, RelationalNode node, String[] params, String[] domainNames, int i, boolean closedWorld) throws Exception {
		// if we have the full set of parameters, count the example
		if(i == params.length) {
			
			if(!closedWorld) {
				String varName = Signature.formatVarName(node.getFunctionName(), params);
				if(!db.contains(varName))
					return;
			}
			
			// to determine if we really have to count the example, we must
			// check if there are any decision parents and count only if all
			// decision parents are true
			Collection<DecisionNode> decisions = node.getDecisionParents();
			if(decisions.size() > 0) {
				for(DecisionNode decision : decisions) {
					if(!decision.isTrue(node.params, params, db, closedWorld))
						return;
				}
			}
			
			countVariable(db, node, params, closedWorld);
			return;
		}
		
		// otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
		Set<String> domain = db.getDomain(domainNames[i]);
		for(String element : domain) {
			params[i] = element;
			countVariable(db, node, params, domainNames, i+1, closedWorld);	
		}		
	}
}
