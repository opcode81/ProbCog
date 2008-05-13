package edu.tum.cs.logic;

public class Implication extends ComplexFormula {
	public Implication(Formula antecedent, Formula consequent) {
		super(new Formula[]{antecedent, consequent});
	}
	
	public String toString() {
		return children[0] + " => " + children[1]; 
	}
}
