package edu.tum.cs.logic;

import java.util.Set;

import edu.tum.cs.srl.Database;

public abstract class UngroundedFormula extends Formula {

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
		throw new RuntimeException("Cannot obtain the set of ground atoms of an ungrounded formula: " + this.toString());
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		throw new RuntimeException("Cannot determine the truth value of an ungrounded formula: " + this.toString());
	}
	
	@Override
	public Formula simplify(Database db) {
		throw new RuntimeException("Cannot simplify ungrounded formula: " + this.toString());
	}
}
