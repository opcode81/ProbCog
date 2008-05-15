package edu.tum.cs.logic;

import java.util.HashMap;

import edu.tum.cs.tools.StringTool;

public class WorldVariables {
	protected HashMap<String, GroundAtom> vars;
	
	public WorldVariables() {
		vars = new HashMap<String, GroundAtom>();
	}
	
	public void add(GroundAtom gndAtom) {
		gndAtom.setIndex(vars.size());
		vars.put(gndAtom.toString(), gndAtom);
	}
	
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
