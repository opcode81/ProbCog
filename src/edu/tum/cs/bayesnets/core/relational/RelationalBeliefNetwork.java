package edu.tum.cs.bayesnets.core.relational;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srldb.Database;

public abstract class RelationalBeliefNetwork extends BeliefNetworkEx {
	/**
	 * maps a node name (function/predicate name) to the corresponding node
	 */
	protected HashMap<String,RelationalNode> relNodesByName;
	/**
	 * maps a node index to the corresponding node
	 */
	protected HashMap<Integer,RelationalNode> relNodesByIdx;
	/**
	 * maps a node name (function/predicate name) to its signature
	 */
	private HashMap<String, Signature> signatures;
	
	public RelationalBeliefNetwork(String xmlbifFile) throws Exception {
		super(xmlbifFile);
		signatures = new HashMap<String, Signature>();
		relNodesByName = new HashMap<String, RelationalNode>();
		relNodesByIdx = new HashMap<Integer, RelationalNode>();
		// store node data
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode d = new RelationalNode(this, nodes[i]);			
			relNodesByName.put(d.name, d);
			relNodesByIdx.put(new Integer(d.index), d);
		}
	}
	
	public RelationalNode getRelationalNode(String name) {
		return relNodesByName.get(name);
	}
	
	public RelationalNode getRelationalNode(int idx) {
		return relNodesByIdx.get(new Integer(idx));
	}
	
	public Collection<RelationalNode> getRelationalNodes() {
		return relNodesByIdx.values();
	}
	
	public boolean isBooleanDomain(Discrete domain) {
		if(domain.getOrder() > 2)
			return false;
		if(domain.getOrder() == 1) {
			if(domain.getName(0).equalsIgnoreCase("true") || domain.getName(0).equalsIgnoreCase("false"))
				return true;
			return false;
		}			
		if(domain.getName(0).equalsIgnoreCase("true") || domain.getName(1).equalsIgnoreCase("true"))
			return true;
		return false;
	}
	
	/**
	 * obtains the names of parents of the variable that is given by a node name and its actual arguments
	 * @param nodeName 
	 * @param actualArgs
	 * @return an array of variable names
	 * @throws Exception
	 */
	public String[] getParentVariableNames(String nodeName, String[] actualArgs) throws Exception {
		RelationalNode child = getRelationalNode(nodeName);
		BeliefNode[] parents = bn.getParents(child.node);
		String[] ret = new String[parents.length];
		for(int i = 0; i < parents.length; i++) {
			RelationalNode parent = getRelationalNode(getNodeIndex(parents[i].getName()));
			StringBuffer varName = new StringBuffer(parent.name + "(");
			String param = null;
			for(int iCur = 0; iCur < parent.params.length; iCur++) {
				for(int iMain = 0; iMain < child.params.length; iMain++) {
					if(child.params[iMain].equals(parent.params[iCur])) {
						param = actualArgs[iMain];
						break;
					}
				}
				if(param == null)
					throw new Exception(String.format("Could not determine parameters of parent '%s' for node '%s'", parent.name, nodeName + actualArgs.toString()));
				varName.append(param);
				if(iCur < parent.params.length-1)
					varName.append(",");
			}
			varName.append(")");
			ret[i] = varName.toString();
		}
		return ret;
	}
	
	public void addSignature(String nodeName, Signature sig) {
		signatures.put(nodeName.toLowerCase(), sig);
	}
	
	public Signature getSignature(String nodeName) {
		return signatures.get(nodeName.toLowerCase());
	}
	
	public Collection<Signature> getSignatures() {
		return signatures.values();
	}
	
	/**
	 * guesses the model's function signatures by assuming the same type whenever the same variable name is used (ignoring any numeric suffixes), setting the domain name to ObjType_x when x is the variable,
	 * and assuming a different domain of return values for each node, using Dom{NodeName} as the domain name.
	 *
	 */
	public void guessSignatures() {
		for(RelationalNode node : relNodesByIdx.values()) {
			String[] argTypes = new String[node.params.length];
			for(int i = 0; i < node.params.length; i++) {
				String param = node.params[i].replaceAll("\\d+", "");
				argTypes[i] = "ObjType_" + param;
			}
			addSignature(node.name, new Signature("Dom" + node.name, argTypes));		
		}
	}

	public class Signature {
		public String returnType;
		public String[] argTypes;
		public Signature(String returnType, String[] argTypes) {
			this.returnType = returnType;
			this.argTypes = argTypes;
		}
	}
	
}


