package edu.tum.cs.logic;

import java.util.Collection;

public abstract class ComplexFormula extends Formula {
	protected Formula[] children;
	
	public ComplexFormula(Collection<Formula> children) {
		this.children = children.toArray(new Formula[children.size()]);
	}
	
	public ComplexFormula(Formula[] children) {
		this.children = children;
	}
}
