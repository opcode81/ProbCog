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
		String varName = nodeName + "(" + RelationalNode.join(",", params) + ")";
		//System.out.println("counting " + varName);
		
		// set the domain indices of all relevant nodes (node itself and parents)
		int domainIndices[] = new int[this.nodes.length];
		for(int i = 0; i < counter.nodeIndices.length; i++) {
			RelationalNode ndCurrent = bn.getRelationalNode(counter.nodeIndices[i]);
			// determine name of the parent node by replacing parameter bindings
			StringBuffer curVarName = new StringBuffer(ndCurrent.name + "(");
			String param = null;
			for(int iCur = 0; iCur < ndCurrent.params.length; iCur++) {
				for(int iMain = 0; iMain < nd.params.length; iMain++) {
					if(nd.params[iMain].equals(ndCurrent.params[iCur])) {
						param = params[iMain];
						break;
					}
				}
				if(param == null)
					throw new Exception(String.format("Could not determine parameters for node '%s' for assignment '%s'", ndCurrent.name, varName));
				curVarName.append(param);
				if(iCur < ndCurrent.params.length-1)
					curVarName.append(",");
			}
			curVarName.append(")");
			// set domain index
			String value = db.getVariableValue(curVarName.toString(), closedWorld);
			if(value == null)
				throw new Exception(String.format("Could not find setting for node named '%s' while processing '%s'", curVarName, varName));
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
	
	public void learn(Database db) throws Exception {		
		for(Variable var : db.getEntries()) {
			countVariable(db, var.nodeName, var.params, false);
		}
	}

	public void learnTyped(Database db, boolean closedWorld) throws Exception {		
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;		
		for(RelationalNode node : bn.getRelationalNodes()) {
			String[] params = new String[node.params.length];			
			countVariable(db, node.name, params, bn.getSignature(node.name).argTypes, 0, closedWorld);			
		}
	}
	
	protected void countVariable(Database db, String nodeName, String[] params, String[] domainNames, int i, boolean closedWorld) throws Exception {
		if(i == params.length) {
			countVariable(db, nodeName, params, true);
			return;
		}
		Set<String> domain = db.getDomain(domainNames[i]);
		for(String element : domain) {
			params[i] = element;
			countVariable(db, nodeName, params, domainNames, i+1, closedWorld);	
		}		
	}
}
