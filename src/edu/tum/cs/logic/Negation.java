package edu.tum.cs.logic;

import java.util.Collection;
import java.util.Vector;

public class Negation extends ComplexFormula {
	public Negation(Formula f) {
		super(new Formula[]{f});
	}
	
	public Negation(Collection<Formula> children) throws Exception {
		super(children);
		if(children.size() != 1)
			throw new Exception("A negation can have but one child.");
	}
	
	public String toString() {
		return "!(" + this.children[0].toString() + ")";
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		return !children[0].isTrue(w);
	}

	@Override
	public Formula toCNF() {
		Formula f = children[0].toCNF();
		if(f instanceof ComplexFormula) {			
			Vector<Formula> negChildren = new Vector<Formula>();
			for(Formula child : ((ComplexFormula)f).children)
				negChildren.add(new Negation(child));
			if(f instanceof Disjunction)
				return new Conjunction(negChildren).toCNF();
			else
				return new Disjunction(negChildren).toCNF();
		}
		else {
			if(f instanceof GroundLiteral) {
				GroundLiteral l = (GroundLiteral)f;
				return new GroundLiteral(!l.isPositive, l.gndAtom);
			}
			else if(f instanceof TrueFalse) {
				TrueFalse tf = (TrueFalse)f;
				return TrueFalse.getInstance(!tf.isTrue());
			}
			throw new RuntimeException("CNF conversion of negation of " + children[0].getClass().getSimpleName() + " not handled.");
		}
	}
}
