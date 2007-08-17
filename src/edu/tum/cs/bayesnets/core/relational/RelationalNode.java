package edu.tum.cs.bayesnets.core.relational;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

public class RelationalNode {
	/**
	 * index into the network's array of BeliefNodes
	 */
	public int index;
	/**
	 * name of the node, which is equal to the function/predicate name without any arguments
	 */
	public String name;
	/**
	 * the list of node parameters
	 */
	public String[] params;
	/**
	 * a reference to the BeliefNode that this node extends
	 */
	public BeliefNode node;
	public Signature sig;
	public boolean isConstant;
	
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
		Pattern namePat = Pattern.compile("(\\w+)\\((.*)\\)");
		String name = node.getName();
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
		this.index = bn.getNodeIndex(name);
		this.node = node;
	}
	
	public String toString() {
		return formatName(this.name, this.params);			
	}
	
	public boolean isBoolean() {
		return sig.returnType.equals("Boolean");
	}

	public static class Signature {
		public String returnType;
		public String[] argTypes;
	
		public Signature(String returnType, String[] argTypes) {
			this.returnType = returnType;
			this.argTypes = argTypes;
		}
		
		public void replaceType(String oldType, String newType) {
			if(this.returnType.equals(oldType))
				this.returnType = newType;
			for(int i = 0; i < argTypes.length; i++) {
				if(argTypes[i].equals(oldType))
					argTypes[i] = newType;
			}
		}
	}
}

