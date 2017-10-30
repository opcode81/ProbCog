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

import java.util.Map;

import probcog.exception.ProbCogException;
import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;

/**
 * Base class for grounded formulas.
 * @author Dominik Jain
 */
public abstract class GroundedFormula extends Formula {

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) {
		return this;
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) {
		throw new UnsupportedOperationException("This method is not intended to be called on grounded formulas.");
	}

	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws ProbCogException {
		// grounded formulas contain no variables; nothing to do
	}
}
