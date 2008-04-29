package edu.tum.cs.logic;

public abstract class ComplexFormula extends Formula {
	protected Formula[] children;
	
	public ComplexFormula(Formula[] children) {
		this.children = children;
	}
}
