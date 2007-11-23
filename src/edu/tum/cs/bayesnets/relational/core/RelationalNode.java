package edu.tum.cs.bayesnets.relational.core;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.srldb.Database;

public class RelationalNode {
	/**
	 * index into the network's array of BeliefNodes
	 */
	public int index;
	/**
	 * name of the node, which is equal to the function/predicate name without any arguments
	 */
	protected String name;
	/**
	 * the list of node parameters
	 */
	public String[] params;
	/**
	 * a reference to the BeliefNode that this node extends
	 */
	public BeliefNode node;
	protected RelationalBeliefNetwork bn;
	public boolean isConstant, isAuxiliary, isPrecondition;
	
	public static String join(String glue, String[] elems) {
		StringBuffer res = new StringBuffer();
		for(int i = 0; i < elems.length; i++) {
			res.append(elems[i]);
			if(i < elems.length-1)
				res.append(glue);
		}
		return res.toString();
	}
	
	public static String formatName(String nodeName, String[] args) {
		return String.format("%s(%s)", nodeName, join(",", args));
	}
	
	/**
	 * extracts the node name (function/predicate name) from a variable name (which contains arguments)
	 * @param varName
	 * @return
	 */
	public static String extractNodeName(String varName) {
		if(varName.contains("("))
			return varName.substring(0, varName.indexOf('('));
		return varName;
	}
	
	public RelationalNode(RelationalBeliefNetwork bn, BeliefNode node) throws Exception {
		this.bn = bn;
		Pattern namePat = Pattern.compile("(\\w+)\\((.*)\\)");
		String name = node.getName();
		// preprocessing: special parent nodes 
		if(name.charAt(0) == '#') { // auxiliary: CPT is meaningless
			isAuxiliary = true;
			name = name.substring(1);
		}
		else if(name.charAt(0) == '+') { // precondition: node is boolean and required to be true
			isPrecondition = true;
			isAuxiliary = true;
			name = name.substring(1);
		}
		// preprocessing noisy-or node
		int sepPos = name.indexOf('|');
		if(sepPos != -1) {
			name = name.substring(0, sepPos);
			String[] norParams = name.substring(sepPos+1).split("\\s*,\\s*");			
		}
		// match function and parameters
		Matcher matcher = namePat.matcher(name);
		if(matcher.matches()) {	// a proper relational node, such as "foo(x,y)"
			this.name = matcher.group(1);
			this.params = matcher.group(2).split("\\s*,\\s*");
			this.isConstant = false;
		}
		else { // constant: usually a node such as "x"
			this.name = name;
			this.params = new String[0];
			this.isConstant = true;
		}
		this.index = bn.getNodeIndex(node);
		this.node = node;
	}
	
	public String toString() {
		return formatName(this.name, this.params);			
	}
	
	public boolean isBoolean() {
		Signature sig = bn.getSignature(this);
		if(sig != null)
			return sig.returnType.equals("Boolean");
		else
			return bn.isBooleanDomain(node.getDomain());
	}

	public static class Signature {
		public String returnType;
		public String[] argTypes;
		public String functionName;
	
		public Signature(String functionName, String returnType, String[] argTypes) {
			this.returnType = returnType;
			this.argTypes = argTypes;
			this.functionName = functionName;
		}
		
		public void replaceType(String oldType, String newType) {
			if(this.returnType.equals(oldType))
				this.returnType = newType;
			for(int i = 0; i < argTypes.length; i++) {
				if(argTypes[i].equals(oldType))
					argTypes[i] = newType;
			}
		}
		
		@Override
		public String toString() {
			return String.format("%s %s(%s)", returnType, functionName, RelationalNode.join(",", argTypes));
		}
	}
	
	/**
	 * gets the name of the function/predicate that this node corresponds to
	 * @return
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * generates a textual representation of the logical literal that this node represents for a certain assignment (and, optionally, substitutions of its parameters) 
	 * @param setting  the value this node is set to given by an index into the node's domain
	 * @param constantValues  mapping of this node's arguments to constants; any number of arguments may be mapped; may be null
	 * @return
	 */
	protected String toLiteral(int setting, HashMap<String,String> constantValues) {
		// predicate name
		StringBuffer sb = new StringBuffer(String.format("%s(", Database.lowerCaseString(name)));
		// add parameters
		for(int i = 0; i < params.length; i++) {
			if(i > 0)
				sb.append(",");
			String value = constantValues != null ? constantValues.get(params[i]) : null;
			if(value == null)
				sb.append(params[i]);
			else
				sb.append(value);
		}
		// add node value (negation as prefix or value of non-boolean variable as additional parameter)
		String value = ((Discrete)node.getDomain()).getName(setting);
		if(isBoolean()) {
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
	
	/**
	 * gets the network this node belongs to
	 */
	protected RelationalBeliefNetwork getNetwork() {
		return bn;
	}
}

