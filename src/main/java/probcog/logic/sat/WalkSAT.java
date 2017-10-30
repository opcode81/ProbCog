 /*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.srl.AbstractVariable;

/**
 * Implementation of the WalkSAT satisfiability solver by Selman et al. (1996)
 * @author Dominik Jain
 */
public class WalkSAT extends SampleSAT {

	public WalkSAT(Iterable<? extends probcog.logic.sat.Clause> kb, PossibleWorld state, WorldVariables vars, Iterable<? extends AbstractVariable<?>> db) throws ProbCogException {
		super(kb, state, vars, db);
	}

	@Override
	protected void makeMove() {
		walkSATMove();
	}
}
