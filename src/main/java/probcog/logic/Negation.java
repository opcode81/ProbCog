/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.logic;


import java.util.Collection;
import java.util.Vector;

import probcog.srl.GenericDatabase;

/**
 * Represents a negated formula.
 * @author Dominik Jain
 */
public class Negation extends ComplexFormula {
	
	//static int cnfDepth = 0; 
	
	public Negation(Formula f) {
        super(new Formula[]{f});
    }

    public Negation(Collection<Formula> children) {
        super(children);
        if (children.size() != 1)
            throw new IllegalArgumentException("A negation must have exactly one child");
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
    	/*
    	try{
    	Negation.cnfDepth++; System.out.printf(String.format("%%%dc", cnfDepth), ' ');
    	System.out.println(this);
    	*/
        Formula f = children[0].toNNF();
        if (f instanceof ComplexFormula) {
            Vector<Formula> negChildren = new Vector<Formula>();
            for (Formula child : ((ComplexFormula) f).children)
                negChildren.add(new Negation(child));
            if (f instanceof Disjunction)
                return new Conjunction(negChildren).toCNF();
            else
                return new Disjunction(negChildren).toCNF();
        } else {
            if (f instanceof GroundLiteral) {
                GroundLiteral l = (GroundLiteral) f;
                return new GroundLiteral(!l.isPositive, l.gndAtom);
            } else if (f instanceof TrueFalse) {
                TrueFalse tf = (TrueFalse) f;
                return TrueFalse.getInstance(!tf.isTrue());
            }
            else if(f instanceof GroundAtom) {
            	return new GroundLiteral(false, (GroundAtom)f);
            }
            else if(f instanceof Atom) {
            	return new Literal(false, (Atom)f);
            }
            else if (f instanceof Literal) {
                Literal l = (Literal) f;
                return new Literal(!l.isPositive, l.atom);
            }
            throw new RuntimeException("CNF conversion of negation of " + children[0].getClass().getSimpleName() + " not handled.");
        }
        /*
    	}
    	finally {
    		Negation.cnfDepth--;
    	}
    	*/
    }
    
    @Override
    public Formula toNNF() {
        Formula f = children[0].toNNF();
        if (f instanceof ComplexFormula) {
            Vector<Formula> negChildren = new Vector<Formula>();
            for (Formula child : ((ComplexFormula) f).children)
                negChildren.add(new Negation(child));
            if (f instanceof Disjunction)
                return new Conjunction(negChildren).toNNF();
            else
                return new Disjunction(negChildren).toNNF();
        } else {
            if (f instanceof GroundLiteral) {
                GroundLiteral l = (GroundLiteral) f;
                return new GroundLiteral(!l.isPositive, l.gndAtom);
            } else if (f instanceof TrueFalse) {
                TrueFalse tf = (TrueFalse) f;
                return TrueFalse.getInstance(!tf.isTrue());
            }
            else if(f instanceof GroundAtom) {
            	return new GroundLiteral(false, (GroundAtom)f);
            }
            throw new RuntimeException("CNF conversion of negation of " + children[0].getClass().getSimpleName() + " not handled.");
        }
    }

    /**
     * this method simplifies the formula (atoms of this formula that are given by the evidence are evaluated to TrueFalse)
     * @param evidence (evidence of the current scenario)
     * @return  
     */
    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
    	// simplify the formula that is negated
        Formula f = this.children[0].simplify(evidence);
        // if it's now an instance of TrueFalse, return its opposite
        if(f instanceof TrueFalse) 
            return ((TrueFalse) f).opposite();
        // otherwise, return the negation of the simplified formula
        return new Negation(f);
    }
}
