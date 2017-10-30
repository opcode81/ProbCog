/*******************************************************************************
 * Copyright (C) 2012 Dominik Jain.
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


public class Equality extends UngroundedFormula {
	public String left, right;

	public Equality(String left, String right) {
		this.left = left;
		this.right = right;
	}

	public String toString() {
		return left + "=" + right;
	}
	
	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) {
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) {
		// TODO it's difficult to determine the types of any constants appearing in Equality statements; they are ignored for now (in the hope that they also appear elsewhere) 
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?,?> db) throws ProbCogException {
		String a = binding.get(left);
		if(a == null) a = left;
		String b = binding.get(right);
		if(b == null) b = right;
		return TrueFalse.getInstance(a.equals(b));
	}

	@Override
	public Formula toCNF() {
		throw new RuntimeException("Cannot convert ungrounded formula to CNF.");
	}
	
	@Override
	public Formula toNNF() {
		throw new RuntimeException("Cannot convert ungrounded formula to NNF.");
	}
}
