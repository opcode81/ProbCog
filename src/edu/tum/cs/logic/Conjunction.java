package edu.tum.cs.logic;

import java.util.Collection;

import edu.tum.cs.tools.StringTool;

public class Conjunction extends ComplexFormula {

	public Conjunction(Collection<Formula> children) {
		super(children);
	}
	
	public String toString() {
		return "(" + StringTool.join(" ^ ", children) + ")";
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		for(Formula child : children)
			if(!child.isTrue(w))
				return false;
		return true;
	}
	
}
