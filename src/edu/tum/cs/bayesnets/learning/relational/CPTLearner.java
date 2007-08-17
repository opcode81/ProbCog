package edu.tum.cs.bayesnets.learning.relational;

import java.util.Set;

import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.core.relational.RelationalNode;
import edu.tum.cs.bayesnets.learning.relational.Database.Variable;

public class CPTLearner extends edu.tum.cs.bayesnets.learning.CPTLearner {
	
	public CPTLearner(RelationalBeliefNetwork bn) throws Exception {
		super(bn);		
	}
	
	/**
	 * count an example (variable setting) by incrementing the counter for the given variable
	 * @param db			the database containing propositions
	 * @param nodeName		node/function name of the variable
	 * @param params		actual parameters
	 * @param closedWorld	whether the closed-world assumption is to be made
	 * @throws Exception
	 */
	protected void countVariable(Database db, String nodeName, String[] params, boolean closedWorld) throws Exception {
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		// get the node and its associated counter
		RelationalNode nd = bn.getRelationalNode(nodeName);
		if(nd == null) {
			String error = String.format("Invalid node name '%s'", nodeName);
			System.err.println(error);
			return;
			//throw new Exception(error);
		}
		ExampleCounter counter = this.counters[nd.index];
		// get the main variable's name
		String varName = RelationalNode.formatName(nodeName, params);
		//System.out.println("counting " + varName);
		// set the domain indices of all relevant nodes (node itself and parents)
		int domainIndices[] = new int[this.nodes.length];
		for(int i = 0; i < counter.nodeIndices.length; i++) {
			RelationalNode ndCurrent = bn.getRelationalNode(counter.nodeIndices[i]);
			// determine the value of the node given the settings implied by the main node
			String value = null, curVarname = ndCurrent.name;
			if(!ndCurrent.isConstant) {
				// determine name of the parent node by replacing parameter bindings			
				StringBuffer curVarName = new StringBuffer(ndCurrent.name + "(");			
				for(int iCur = 0; iCur < ndCurrent.params.length; iCur++) {
					// search for the iCur-th parameter in the main node
					String param = null;
					for(int iMain = 0; iMain < nd.params.length; iMain++) {
						if(nd.params[iMain].equals(ndCurrent.params[iCur])) {
							param = params[iMain];
							break;
						}
					}
					if(param == null)
						throw new Exception(String.format("Could not determine parameter '%s' of node '%s' while traversing dependencies of '%s'", ndCurrent.params[iCur], ndCurrent.name, varName));
					curVarName.append(param);
					if(iCur < ndCurrent.params.length-1)
						curVarName.append(",");
				}
				curVarName.append(")");
				curVarname = curVarName.toString();
				//System.out.println("  relevant node " + curVarName);
				// set domain index
				value = db.getVariableValue(curVarName.toString(), closedWorld);
			}
			else {
				for(int iMain = 0; iMain < nd.params.length; iMain++) {
					if(nd.params[iMain].equals(ndCurrent.name)) {
						value = params[iMain];
						break;
					}			
				}
			}
			if(value == null)
				throw new Exception(String.format("Could not find setting for node named '%s' while processing '%s'", curVarname, varName));
			Discrete dom = (Discrete)(ndCurrent.node.getDomain());
			int domain_idx = dom.findName(value);
			if(domain_idx == -1) {	
				String[] domElems = new String[dom.getOrder()];
				for(int j = 0; j < domElems.length; j++)
					domElems[j] = dom.getName(j);
				throw new Exception(String.format("'%s' not found in domain of %s {%s} while processing %s", value, ndCurrent.name, RelationalNode.join(",", domElems), varName));
			}
			domainIndices[ndCurrent.index] = domain_idx;
		}
		// count this example
		counter.count(domainIndices);
	}
	
	/**
	 * learn the CPTs from only the data that is given in the database (relations not in the database are not considered because the closed-world assumption is not being made)
	 * @param db
	 * @throws Exception
	 */
	public void learn(Database db) throws Exception {		
		for(Variable var : db.getEntries()) {
			countVariable(db, var.nodeName, var.params, false);
		}
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
		for(RelationalNode node : bn.getRelationalNodes()) {
			if(node.isConstant) continue;
			if(verbose) System.out.println("  " + node.name);
			String[] params = new String[node.params.length];			
			countVariable(db, node.name, params, bn.getSignature(node.name).argTypes, 0, closedWorld);			
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
	protected void countVariable(Database db, String nodeName, String[] params, String[] domainNames, int i, boolean closedWorld) throws Exception {
		// if we have the full set of parameters, count the example
		if(i == params.length) {
			if(!closedWorld) {
				String varName = RelationalNode.formatName(nodeName, params);
				if(!db.contains(varName))
					return;
			}
			countVariable(db, nodeName, params, closedWorld);
			return;
		}
		// otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
		Set<String> domain = db.getDomain(domainNames[i]);
		for(String element : domain) {
			params[i] = element;
			countVariable(db, nodeName, params, domainNames, i+1, closedWorld);	
		}		
	}
}
