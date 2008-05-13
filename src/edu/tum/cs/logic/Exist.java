package edu.tum.cs.logic;

import java.util.Collection;

import edu.tum.cs.tools.StringTool;

public class Exist extends Formula {
	Formula f;
	Collection<String> vars;
	
	public Exist(Collection<String> vars, Formula f) {
		this.vars = vars;
		this.f = f;
	}
	
	public String toString() {
		return "EXIST " + StringTool.join(",", vars) + " (" + f.toString() + ")";
	}
}
