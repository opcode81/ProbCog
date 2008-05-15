package edu.tum.cs.logic;

import java.util.Collection;

public class Implication extends ComplexFormula {
	public Implication(Collection<Formula> c) {
		super(c);
	}
	
	public Implication(Formula antecedent, Formula consequent) {
		super(new Formula[]{antecedent, consequent});
	}
	
	public String toString() {
		return "(" + children[0] + " => " + children[1] + ")"; 
	}

	@Override
	public boolean isTrue(PossibleWorld w) {		
		return !children[0].isTrue(w) || children[1].isTrue(w);
	}
}
