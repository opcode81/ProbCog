package edu.tum.cs.logic;

import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;

import edu.tum.cs.tools.StringTool;

public class Conjunction extends ComplexFormula {

	public Conjunction(Collection<Formula> children) {
		super(children);
	}
	
	public Conjunction(Formula ... children) {
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

	@Override
	public Formula toCNF() {
		Collection<Formula> clauses = new Vector<Formula>();
		for(Formula child : this.children) {
			child = child.toCNF();
			if(child instanceof Conjunction) {
				Conjunction conj = (Conjunction)child;
				clauses.addAll(Arrays.asList(conj.children));
			}
			else {
				clauses.add(child);
			}
		}
		return new Conjunction(clauses);
	}	
}
