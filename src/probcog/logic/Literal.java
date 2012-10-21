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

import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;

/**
 * Represents an ungrounded literal.
 * @author Dominik Jain
 */
public class Literal extends UngroundedFormula {
	public boolean isPositive;
	public Atom atom;
	
	public Literal(boolean isPositive, Atom atom) {
		this.atom = atom;
		this.isPositive = isPositive;
	}
	
	public String toString() {
		return isPositive ? atom.toString() : "!" + atom;
	}

	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws Exception {
		atom.getVariables(db, ret);	
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) throws Exception {
		atom.addConstantsToModel(m);
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) throws Exception {
		return new GroundLiteral(isPositive, (GroundAtom)atom.ground(binding, vars, db));
	}

	@Override
	public Formula toCNF() {
		return this;
	}
	
	@Override
	public Formula toNNF() {
		return this;
	}
}
