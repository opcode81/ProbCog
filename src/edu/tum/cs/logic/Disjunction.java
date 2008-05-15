package edu.tum.cs.logic;

import java.util.Collection;

import edu.tum.cs.tools.StringTool;

public class Disjunction extends ComplexFormula {

	public Disjunction(Collection<Formula> children) {
		super(children);
	}
	
	public String toString() {
		return "(" + StringTool.join(" v ", children) + ")";
	}
	
	@Override
	public boolean isTrue(PossibleWorld w) {
		for(Formula child : children)
			if(child.isTrue(w))
				return true;
		return false;
	}	
}
