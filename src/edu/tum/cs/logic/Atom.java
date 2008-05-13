package edu.tum.cs.logic;

import java.util.Collection;

import edu.tum.cs.tools.StringTool;

public class Atom extends Formula {

	public Collection<String> params;
	public String predName;
	
	public Atom(String predName, Collection<String> params) {
		this.predName = predName;
		this.params = params;
	}
	
	@Override
	public String toString() {
		return predName + "(" + StringTool.join(",", params) + ")";
	}
}
