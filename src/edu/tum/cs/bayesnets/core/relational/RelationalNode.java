package edu.tum.cs.bayesnets.core.relational;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

public class RelationalNode {
	public int index;
	public String name;
	public String[] params;
	public BeliefNode node;
	
	public static String join(String glue, String[] elems) {
		StringBuffer res = new StringBuffer();
		for(int i = 0; i < elems.length; i++) {
			res.append(elems[i]);
			if(i < elems.length-1)
				res.append(glue);
		}
		return res.toString();
	}
	
	public RelationalNode(RelationalBeliefNetwork bn, BeliefNode node) throws Exception {
		Pattern namePat = Pattern.compile("(\\w+)\\((.*)\\)");
		String name = node.getName();
		Matcher matcher = namePat.matcher(name);
		if(!matcher.matches()) 
			throw new Exception(String.format("Node '%s' has an invalid name", name));
		this.name = matcher.group(1);
		this.params = matcher.group(2).split("\\s*,\\s*");
		this.index = bn.getNodeIndex(name);
		this.node = node;
	}
	
	public String toString() {
		return name + "(" + join(",", params);			
	}
}