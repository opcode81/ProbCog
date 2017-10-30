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
 * Represents a logical biimplication.
 * @author Dominik Jain
 */
public class Biimplication extends ComplexFormula {
	
	public Biimplication(Collection<Formula> parts) {
		super(parts);
		if(parts.size() != 2)
			throw new IllegalArgumentException("A biimplication must have exactly two children.");
	}

	public Biimplication(Formula f1, Formula f2) {
		super(new Formula[]{f1, f2});
	}

	public String toString() {
		return "(" + children[0] + " <=> " + children[1] + ")";
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		return children[0].isTrue(w) == children[1].isTrue(w);
	}

	@Override
	public Formula toCNF() {
		Formula c1 = new Disjunction(new Negation(children[0]), children[1]);
		Formula c2 = new Disjunction(children[0], new Negation(children[1]));
		return new Conjunction(c1, c2).toCNF();
	}
	
	@Override
	public Formula toNNF() {
		Formula c1 = new Disjunction(new Negation(children[0]), children[1]);
		Formula c2 = new Disjunction(children[0], new Negation(children[1]));
		return new Conjunction(c1, c2).toNNF();		
	}

    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        Formula c1 = new Disjunction(new Negation(children[0]), children[1]);
        Formula c2 = new Disjunction(children[0], new Negation(children[1]));
        return (new Conjunction(c1, c2)).simplify(evidence);
    }
}
