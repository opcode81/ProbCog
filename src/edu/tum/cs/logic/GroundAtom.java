package edu.tum.cs.logic;

public class GroundAtom implements GroundFormula {
	protected String predicate;
	protected String[] args;
	
	public GroundAtom(String predicate, String[] args) {
		this.predicate = predicate;
		this.args = args;
	}

	public boolean isTrue(PossibleWorld w) {
		return w.isTrue(this);
	}
}
