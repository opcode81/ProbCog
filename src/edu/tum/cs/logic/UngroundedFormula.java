package edu.tum.cs.logic;

import java.util.Set;

public abstract class UngroundedFormula extends Formula {

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
		throw new RuntimeException("Cannot obtain the set of ground atoms of an ungrounded formula.");
	}

	@Override
	public boolean isTrue(PossibleWorld w) {
		throw new RuntimeException("Cannot determine the truth value of an ungrounded formula.");
	}
}
