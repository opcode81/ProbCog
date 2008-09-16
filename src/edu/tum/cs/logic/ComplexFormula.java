package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.tum.cs.bayesnets.relational.core.Database;

public abstract class ComplexFormula extends Formula {
	public Formula[] children;
	
	public ComplexFormula(Collection<Formula> children) {
		this.children = children.toArray(new Formula[children.size()]);
	}
	
	public ComplexFormula(Formula ... children) {
		this.children = children;
	}
	
	/*public ComplexFormula(Formula[] children) {
		this.children = children;
	}*/
	
	@Override
	public void getVariables(Database db, Map<String, String> ret) {
		for(Formula f : children)
			f.getVariables(db, ret);
	}
	
	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, Database db) throws Exception {
		Vector<Formula> groundChildren = new Vector<Formula>();
		for(Formula child : children) {
			groundChildren.add(child.ground(binding, vars, db));
		}
		return this.getClass().getConstructor(Collection.class).newInstance(groundChildren);
	}
	
	public void getGroundAtoms(Set<GroundAtom> ret) {
		for(Formula child : children)
			child.getGroundAtoms(ret);
	}
}
