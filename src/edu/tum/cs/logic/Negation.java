package edu.tum.cs.logic;

import java.util.Collection;

public class Negation extends ComplexFormula {
	public Negation(Formula f) {
		super(new Formula[]{f});
	}
	
	public Negation(Collection<Formula> children) throws Exception {
		super(children);
		if(children.size() != 1)
			throw new Exception("A negation can have but one child.");
	}
	
	public String toString() {
		return "!(" + this.children[0].toString() + ")";
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		return !children[0].isTrue(w);
	}
}
