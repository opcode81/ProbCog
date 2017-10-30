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

import probcog.srl.GenericDatabase;

/**
 * Represents a logical implication.
 * @author Dominik Jain
 */
public class Implication extends ComplexFormula {
	public Implication(Collection<Formula> c) {
		super(c);
		if(c.size() != 2)
			throw new IllegalArgumentException("An implication must have exactly two children (antecedent and consequent).");
	}
	
	public Implication(Formula antecedent, Formula consequent) {
		super(new Formula[]{antecedent, consequent});
	}
	
	public String toString() {
		return "(" + children[0] + " => " + children[1] + ")"; 
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {		
		return !children[0].isTrue(w) || children[1].isTrue(w);
	}

	@Override
	public Formula toCNF() {		
		return new Disjunction(new Negation(children[0]), children[1]).toCNF();
	}
	
	@Override
	public Formula toNNF() {		
		return new Disjunction(new Negation(children[0]), children[1]).toNNF();
	}

    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        return (new Disjunction(new Negation(children[0]), children[1])).simplify(evidence);
    }
}
