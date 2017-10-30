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
package probcog.logic.sat;

import probcog.exception.ProbCogException;
import probcog.logic.ComplexFormula;
import probcog.logic.Disjunction;
import probcog.logic.Formula;
import probcog.logic.GroundAtom;
import probcog.logic.GroundLiteral;
import probcog.logic.IPossibleWorld;
import probcog.srl.GenericDatabase;
import edu.tum.cs.util.StringTool;

/**
 * Represents a logical clause.
 * @author Dominik Jain
 */
public class Clause extends ComplexFormula {
	
	public GroundLiteral[] lits;
	
	public Clause(Formula f) throws ProbCogException {
		//System.out.println("generating clause from " + f.toString());
		if(f instanceof GroundLiteral) {
			lits = new GroundLiteral[1];
			lits[0] = (GroundLiteral)f;
		}		
		else if(f instanceof Disjunction) {
			Disjunction d = (Disjunction)f;
			lits = new GroundLiteral[d.children.length];
			// add each child of the disjunction
			for(int i = 0; i < lits.length; i++) {
				// the child must be a ground literal
				if(d.children[i] instanceof GroundLiteral)
					lits[i] = (GroundLiteral)d.children[i];
				else
					throw new ProbCogException("Disjunction contains child of unacceptable type " + d.children[i].getClass().getSimpleName() + "; only GroundLiterals allowed.");
				// check if we previously added the negative literal or the same literal
				for(int j = 0; j < i; j++)
					if(lits[i].gndAtom == lits[j].gndAtom) {
						if(lits[i].isPositive != lits[j].isPositive)
							throw new TautologyException(d);
						throw new ProbCogException("Tried to create SAT clause from disjunction with duplicate ground atoms: " + d);
					}
			}
		}
		else if(f instanceof GroundAtom) {
			lits = new GroundLiteral[1];
			lits[0] = new GroundLiteral(true, (GroundAtom)f);
		}
		else
			throw new ProbCogException("Instance of type " + f.getClass().getSimpleName() + " cannot be treated as a clause");
	}
	
	public static class TautologyException extends ProbCogException {
		private static final long serialVersionUID = 1L;

		public TautologyException(Disjunction d) {
			super("Tried to create SAT clause from tautology: " + d);
		}
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		for(GroundLiteral lit : lits) 
			if(lit.isTrue(w))
				return true;
		return false;		
	}

	@Override
	public Formula toCNF() {
		return this;
	}
	
	public String toString() {
		return StringTool.join(" v ", this.lits);
	}

    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
    	throw new UnsupportedOperationException("Not supported yet.");
    }

	@Override
	public Formula toNNF() {
		return this;	
	}
}
