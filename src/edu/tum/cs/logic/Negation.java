package edu.tum.cs.logic;

public class Negation extends ComplexFormula {
	public Negation(Formula f) {
		super(new Formula[]{f});
	}
	
	public String toString() {
		return "!(" + this.children[0].toString() + ")";
	}
}
