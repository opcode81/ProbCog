package edu.tum.cs.logic;

import java.util.Collection;

public class Biimplication extends ComplexFormula {
	
	public Biimplication(Collection<Formula> parts) throws Exception {
		super(parts);
		if(parts.size() != 2)
			throw new Exception("A biimplication must have exactly two children.");
	}
	
	public Biimplication(Formula f1, Formula f2) {
		super(new Formula[]{f2, f2});
	}
	
	public String toString() {
		return "(" + children[0] + " <=> " + children[1] + ")"; 
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {		
		return children[0].isTrue(w) == children[1].isTrue(w);
	}
}
