/*
 * Created on Aug 5, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl;

import java.util.Vector;

import edu.tum.cs.util.StringTool;

public class RelationKey {
	/**
	 * the name of the relation
	 */
	public String relation;
	/**
	 * list of indices of the parameters that make up a key
	 */
	public Vector<Integer> keyIndices;
	/**
	 * the original arguments with which the relation key was declared (i.e. list of parameters with "_" as entries for functionally determined arguments)
	 */
	protected String[] arguments;
	
	public RelationKey(String relation, String[] arguments) {
		this.relation = relation;
		this.arguments = arguments;
		keyIndices = new Vector<Integer>();
		for(int i = 0; i < arguments.length; i++) {
			if(!arguments[i].equals("_")) {
				keyIndices.add(i);
			}
		}
	}
	
	public String toString() {
		return "relationKey " + relation + "(" + StringTool.join(",", arguments) + ");";
	}
}