package edu.tum.cs.logic;

public class Disjunction extends ComplexFormula {

	public Disjunction(Formula[] children) {
		super(children);
	}
	
	@Override
	public boolean isTrue(PossibleWorld w) {
		for(Formula child : children)
			if(child.isTrue(w))
				return true;
		return false;
	}
	
}
