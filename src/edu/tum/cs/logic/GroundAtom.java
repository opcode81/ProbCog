package edu.tum.cs.logic;

import java.util.HashMap;
import java.util.Set;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.tools.StringTool;

public class GroundAtom extends Formula {
	public String predicate;
	public String[] args;
	public int index; 
	
	public GroundAtom(String predicate, String[] args) {
		this.predicate = predicate;
		this.args = args;
	}

	public boolean isTrue(PossibleWorld w) {
		return w.isTrue(this);
	}
	
	public void setIndex(int i) {
		index = i;
	}

	@Override
	public void getVariables(Database db, HashMap<String, String> ret) {
	}

	@Override
	public Formula ground(HashMap<String, String> binding, WorldVariables vars, Database db) {
		return this;
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
		ret.add(this);		
	}
	
	@Override
	public String toString() {
		return predicate + "(" + StringTool.join(",", args) + ")";
	}
}
