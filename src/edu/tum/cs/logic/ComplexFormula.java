package edu.tum.cs.logic;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.tum.cs.srl.GenericDatabase;
import edu.tum.cs.srl.RelationalModel;

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
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws Exception {
		for(Formula f : children)
			f.getVariables(db, ret);
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) throws Exception {
		for(Formula f : children)
			f.addConstantsToModel(m);
	}
	
	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) throws Exception {
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
