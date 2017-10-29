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

import java.util.Set;

import probcog.srl.GenericDatabase;

/**
 * Represents an ungrounded formula.
 * @author Dominik Jain
 */
public abstract class UngroundedFormula extends Formula {

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
		throw new RuntimeException("Cannot obtain the set of ground atoms of an ungrounded formula: " + this.toString());
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		throw new RuntimeException("Cannot determine the truth value of an ungrounded formula: " + this.toString());
	}
	
	@Override
	public Formula simplify(GenericDatabase<?, ?> db) {
		throw new RuntimeException("Cannot simplify ungrounded formula: " + this.toString());
	}
}
