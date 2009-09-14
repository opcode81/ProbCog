package edu.tum.cs.logic;

import java.util.Map;

import edu.tum.cs.srl.Database;

public class Literal extends UngroundedFormula {
	public boolean isPositive;
	public Atom atom;
	
	public Literal(boolean isPositive, Atom atom) {
		this.atom = atom;
		this.isPositive = isPositive;
	}
	
	public String toString() {
		return isPositive ? atom.toString() : "!" + atom;
	}

	@Override
	public void getVariables(Database db, Map<String, String> ret) throws Exception {
		atom.getVariables(db, ret);	
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, Database db) throws Exception {
		return new GroundLiteral(isPositive, (GroundAtom)atom.ground(binding, vars, db));
	}

	@Override
	public Formula toCNF() {
		return this;
	}
}
