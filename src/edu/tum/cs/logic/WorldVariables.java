package edu.tum.cs.logic;

import java.util.HashMap;

import edu.tum.cs.tools.StringTool;

/**
 * contains the set of variables of a propositionalized first-order knowledge base, 
 * i.e. a set of ground atoms, where each is assigned a unique index 
 * (which can be used to represent a possible world as an array of booleans) 
 * 
 * @author jain
 */
public class WorldVariables {
	protected HashMap<String, GroundAtom> vars;
	
	/**
	 * constructs an empty set of variables
	 */
	public WorldVariables() {
		vars = new HashMap<String, GroundAtom>();
	}
	
	/**
	 * adds a variable (ground atom)
	 * @param gndAtom
	 */
	public void add(GroundAtom gndAtom) {
		gndAtom.setIndex(vars.size());
		vars.put(gndAtom.toString(), gndAtom);
	}
	
	/**
	 * retrieves the variable (ground atom) that corresponds the given string representation
	 * @param gndAtom
	 * @return
	 */
	public GroundAtom get(String gndAtom) {
		return vars.get(gndAtom);
	}
	
	public int size() {
		return vars.size();
	}
	
	public String toString() {
		return "<" + StringTool.join(" ", vars.keySet()) + ">";		
	}
}
