package edu.tum.cs.bayesnets.relational.learning;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.bayesnets.relational.core.ParentGrounder;
import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.relational.core.RelationalNode;
import edu.tum.cs.bayesnets.relational.core.Database.Variable;

public class CPTLearner extends edu.tum.cs.bayesnets.learning.CPTLearner {
	
	protected HashMap<Integer, HashMap<String, Integer>> marginals;
	
	public CPTLearner(RelationalBeliefNetwork bn) throws Exception {
		super(bn);		
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
		String varName = RelationalNode.formatName(node.getFunctionName(), params);
		//System.out.println("counting " + varName);
		
		// obtain all groundings of the relevant variables
		ParentGrounder pg = bn.getParentGrounder(node);
		Vector<Map<Integer, String[]>> groundings = pg.getGroundings(params, db);
		if(groundings == null) {
			System.err.println("Variable " + RelationalNode.formatName(node.getFunctionName(), params)+ " skipped because parents could not be grounded.");
			return;
		}

		//System.out.println();
		/*HashMap<String, Integer> counts = marginals.get(node.index);
		if(counts == null) {
			counts = new HashMap<String, Integer>();
			marginals.put(node.index, counts);
		}*/
		
		double exampleWeight = 1.0;

		// do some precomputations
		// - for average of conditional probabilities compute the homogeneity of the relational parents to obtain suitable example weights
		if(node.aggregator != null && node.aggregator.equals("AVG") && node.parentMode != null && node.parentMode.equals("CP")) {
			// create a vector of counts/probabilities
			// first get the number of configurations that are possible for each parent
			int dim = 1;
			RelationalNode[] parents = bn.getRelationalParents(node);
			Vector<Integer> relevantParentIndices = new Vector<Integer>();
			Vector<Integer> precondParentIndices = new Vector<Integer>();
			for(RelationalNode parent : parents) {
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
						throw new Exception("Could not find value '" + value + "' in domain of " + ndCurrent.toString() + " {" + RelationalNode.join(",", domain) + "}");
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
			
		// set the domain indices of all relevant nodes (node itself and parents)			
		for(Map<Integer, String[]> paramSets : groundings) { // for each grounding...
			// consider the concrete parents
			boolean countExample = true;
			int domainIndices[] = new int[this.nodes.length];
			for(int i = 0; i < counter.nodeIndices.length; i++) {
				// get the corresponding node object
				RelationalNode ndCurrent = bn.getRelationalNode(counter.nodeIndices[i]);
				// determine the value of the node given the parameter settings implied by the main node
				String value = ndCurrent.getValueInDB(paramSets.get(ndCurrent.index), db, closedWorld);
				// if the node is a precondition, i.e. it is required to be true, check that it really is
				if(ndCurrent.isPrecondition && !value.equalsIgnoreCase("true")) {
					// it's not, so skip this example
					countExample = false;
					break;
				}
				if(value == null)
					throw new Exception(String.format("Could not find setting for node named '%s' while processing '%s'", ndCurrent.getName(), varName));
				// get the current node's domain and the index of its setting
				Discrete dom = (Discrete)(ndCurrent.node.getDomain());
				int domain_idx = dom.findName(value);
				if(domain_idx == -1) {	
					String[] domElems = new String[dom.getOrder()];
					for(int j = 0; j < domElems.length; j++)
						domElems[j] = dom.getName(j);
					throw new Exception(String.format("'%s' not found in domain of %s {%s} while processing %s", value, ndCurrent.getFunctionName(), RelationalNode.join(",", domElems), varName));
				}
				domainIndices[ndCurrent.index] = domain_idx;
				// side affair: learn the CPT of constant nodes here by incrementing the counter
				if(ndCurrent.isConstant) {
					int[] constantDomainIndices = new int[this.nodes.length];
					constantDomainIndices[ndCurrent.index] = domain_idx;
					this.counters[ndCurrent.index].count(constantDomainIndices);
				}
			}
			// count this example
			if(countExample)
				counter.count(domainIndices, exampleWeight);
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
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;		
		for(RelationalNode node : bn.getRelationalNodes()) { // for each node...
			if(node.isConstant || node.isBuiltInPred()) // ignore constant nodes as they do not correspond to logical atoms 
				continue; 
			if(verbose) System.out.println("  " + node.getName());
			// consider all possible bindings for the node's parameters and count
			String[] params = new String[node.params.length];			
			countVariable(db, node, params, bn.getSignature(node.getFunctionName()).argTypes, 0, closedWorld);
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
				String varName = RelationalNode.formatName(node.getFunctionName(), params);
				if(!db.contains(varName))
					return;
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
