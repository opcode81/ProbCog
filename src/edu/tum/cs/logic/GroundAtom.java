package edu.tum.cs.logic;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.tools.StringTool;

public class GroundAtom extends Formula {
	public String predicate;
	public String[] args;
	public int index; 
	
	public GroundAtom(String predicate, String[] args) {
		this.predicate = predicate;
		this.args = args;
		index = -1;
	}
	
	public GroundAtom(String gndAtom) {
		Pattern p = Pattern.compile("(\\w+)\\(([^\\)]+)\\)");
		Matcher m = p.matcher(gndAtom);
		predicate = m.group(1);
		args = m.group(2).split("\\s*,\\s*");
		index = -1;
	}

	public boolean isTrue(IPossibleWorld w) {
		return w.isTrue(this);
	}
	
	public void setIndex(int i) {
		index = i;
	}

	@Override
	public void getVariables(Database db, Map<String, String> ret) {
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, Database db) {
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
