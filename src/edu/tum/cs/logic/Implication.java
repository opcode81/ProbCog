package edu.tum.cs.logic;

import java.util.Collection;

public class Implication extends ComplexFormula {
	public Implication(Collection<Formula> c) throws Exception {
		super(c);
		if(c.size() != 2)
			throw new Exception("An implication must have exactly two children (antecedent and consequent).");
	}
	
	public Implication(Formula antecedent, Formula consequent) {
		super(new Formula[]{antecedent, consequent});
	}
	
	public String toString() {
		return "(" + children[0] + " => " + children[1] + ")"; 
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {		
		return !children[0].isTrue(w) || children[1].isTrue(w);
	}

	@Override
	public Formula toCNF() {		
		return new Disjunction(new Negation(children[0]), children[1]).toCNF();
	}
}
