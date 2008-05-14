package edu.tum.cs.logic;

public class Biimplication extends ComplexFormula {
	public Biimplication(Formula f1, Formula f2) {
		super(new Formula[]{f2, f2});
	}
	
	public String toString() {
		return "(" + children[0] + " <=> " + children[1] + ")"; 
	}
}
