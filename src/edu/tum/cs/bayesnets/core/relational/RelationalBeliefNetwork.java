package edu.tum.cs.bayesnets.core.relational;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.relational.RelationalNode.Signature;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.datadict.DataDictionary;

public abstract class RelationalBeliefNetwork extends BeliefNetworkEx {
	/**
	 * maps a node name (function/predicate name) to the corresponding node
	 */
	protected HashMap<String,RelationalNode> relNodesByName;
	/**
	 * maps a node index to the corresponding node
	 */
	protected HashMap<Integer,RelationalNode> relNodesByIdx;
	
	public RelationalBeliefNetwork(String xmlbifFile) throws Exception {
		super(xmlbifFile);
		relNodesByName = new HashMap<String, RelationalNode>();
		relNodesByIdx = new HashMap<Integer, RelationalNode>();
		// store node data
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode d = new RelationalNode(this, nodes[i]);			
			relNodesByName.put(d.name.toLowerCase(), d);
			relNodesByIdx.put(new Integer(d.index), d);
		}
	}
	
	public RelationalNode getRelationalNode(String name) {
		return relNodesByName.get(name.toLowerCase());
	}
	
	public RelationalNode getRelationalNode(int idx) {
		return relNodesByIdx.get(new Integer(idx));
	}
	 
	public RelationalNode getRelationalNode(BeliefNode node) {
		return getRelationalNode(RelationalNode.extractNodeName(node.getName()));
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
		getRelationalNode(nodeName).sig = sig;
	}
	
	/**
	 * replace a type by a new type in all node signatures
	 * @param oldType
	 * @param newType
	 */
	public void replaceType(String oldType, String newType) {
		for(RelationalNode node : this.relNodesByIdx.values()) {
			node.sig.replaceType(oldType, newType);
		}
	}
	
	public Signature getSignature(String nodeName) throws Exception {
		RelationalNode node = getRelationalNode(nodeName);
		if(node == null)
			throw new Exception("Could not determine signature of " + nodeName);
		return node.sig;
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
			String retType = isBooleanDomain(((Discrete)node.node.getDomain())) ? "Boolean" : "Dom" + node.name; 
			addSignature(node.name, new Signature(retType, argTypes));		
		}
	}

	public RelationalNode[] getRelationalParents(RelationalNode node) {
		BeliefNode[] p = this.bn.getParents(node.node);
		RelationalNode[] p2 = new RelationalNode[p.length];
		for(int i = 0; i < p.length; i++) {
			p2[i] = getRelationalNode(RelationalNode.extractNodeName(p[i].getName()));
		}
		return p2;
	}
	
	public void toMLN(PrintStream out) throws Exception {		
		// write domain declarations
		HashSet<String> handled = new HashSet<String>();
		HashMap<String, Vector<String>> domains = new HashMap<String,Vector<String>>();
		for(RelationalNode node : getRelationalNodes()) {	
			Signature sig = getSignature(node.name);
			if(sig.returnType.equals("Boolean"))
				continue;
			if(handled.contains(sig.returnType))
				continue;
			handled.add(sig.returnType);
			Vector<String> d = new Vector<String>();
			out.printf("%s = {", Database.lowerCaseString(sig.returnType));
			String[] dom = getDiscreteDomainAsArray(node.node);
			for(int i = 0; i < dom.length; i++) {
				if(i > 0) out.print(", ");
				String elem = Database.upperCaseString(dom[i]); 
				out.print(elem);
				d.add(elem);
			}
			out.println("}");
			domains.put(sig.returnType, d);
		}
		out.println();
		
		// write predicate declarations
		for(RelationalNode node : getRelationalNodes()) {
			Signature sig = getSignature(node.name);
			String[] argTypes;
			if(sig.returnType.equals("Boolean"))
				argTypes = new String[sig.argTypes.length];
			else {
				argTypes = new String[sig.argTypes.length + 1];
				argTypes[argTypes.length-1] = Database.lowerCaseString(sig.returnType);
			}
			for(int i = 0; i < sig.argTypes.length; i++)
				argTypes[i] = Database.lowerCaseString(sig.argTypes[i]);
			out.printf("%s(%s)\n", Database.lowerCaseString(node.name), RelationalNode.join(", ", argTypes));			
		}
		out.println();
		
		// mutual exclusiveness and exhaustiveness
		for(RelationalNode node : getRelationalNodes()) {
			if(!node.isBoolean()) {
				out.print(Database.lowerCaseString(node.name));
				out.print('(');
				for(int i = 0; i <= node.params.length; i++) {
					if(i > 0) out.print(", ");
					out.printf("a%d", i);
					if(i == node.params.length) out.print('!');
				}
				out.println(")");
			}
		}
		out.println();
		
		// write formulas
		int[] order = getTopologicalOrder();
		for(int i = 0; i < order.length; i++) {
			CPF cpf = getRelationalNode(order[i]).node.getCPF();
			int[] addr = new int[cpf.getDomainProduct().length];
			walkCPD_MLNformulas(out, cpf, addr, 0);
		}
	}
	
	protected String toPredicate(RelationalNode node, int setting) {
		String value = ((Discrete)node.node.getDomain()).getName(setting);
		StringBuffer sb = new StringBuffer(String.format("%s(%s", Database.lowerCaseString(node.name), RelationalNode.join(",", node.params)));
		if(node.isBoolean()) {
			if(value.equalsIgnoreCase("false"))
				sb.insert(0, '!');
		}
		else {
			sb.append(',');
			sb.append(Database.upperCaseString(value));			
		}
		sb.append(')');
		return sb.toString();
	}
	
	protected void walkCPD_MLNformulas(PrintStream out, CPF cpf, int[] addr, int i) {
		BeliefNode[] nodes = cpf.getDomainProduct();
		if(i == addr.length) {			
			StringBuffer sb = new StringBuffer();
			for(int j = 0; j < addr.length; j++) {
				if(j > 0)
					sb.append(" ^ ");
				sb.append(toPredicate(getRelationalNode(nodes[j]), addr[j]));
			}
			int realAddr = cpf.addr2realaddr(addr);
			double value = ((ValueDouble)cpf.get(realAddr)).getValue();
			double weight = Math.log(value);
			if(Double.isInfinite(weight)) weight = -100.0;
			out.printf("%f %s\n", weight, sb.toString()); 
		}
		else {
			Discrete dom = (Discrete)nodes[i].getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				walkCPD_MLNformulas(out, cpf, addr, i+1);
			}
		}
	}
}


