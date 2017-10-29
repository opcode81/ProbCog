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

import probcog.srl.GenericDatabase;
import probcog.srl.Signature;
import probcog.srl.directed.RelationalBeliefNetwork;

/**
 * Represents a possible world, where the values are given in a database.
 * @author Dominik Jain
 */
public class PossibleWorldFromDatabase implements IPossibleWorld {

	GenericDatabase<?,?> db;
	RelationalBeliefNetwork rbn;
	boolean closedWorld;
	
	public PossibleWorldFromDatabase(RelationalBeliefNetwork rbn, GenericDatabase<?,?> db, boolean closedWorld) {
		this.db = db;
		this.rbn = rbn;
		this.closedWorld = closedWorld;
	}

	public boolean isTrue(GroundAtom ga) {
		try {
			Signature sig = rbn.getSignature(ga.predicate);			
			if(sig.isBoolean()) {
				String value = db.getSingleVariableValue(ga.toString(), closedWorld);
				if(value == null)
					throw new RuntimeException("Value of " + ga + " not in the database that is used as a possible world; perhaps it must always be given because it is used in a precondition/decision node.");
				boolean tv = value.equalsIgnoreCase("True");
				//System.out.println("value of atom " + ga + " corresponding to boolean function is " + (tv ? "true" : "false"));
				return tv;
			}
			else {
				String varName = rbn.gndAtom2VarName(ga);
				String value = db.getSingleVariableValue(varName, closedWorld);
				if(value == null)
					throw new RuntimeException("Value of " + varName + " not in the database that is used as a possible world; perhaps it must always be given because it is used in a precondition/decision node.");
				boolean tv = value.equals(ga.args[ga.args.length-1]);
				//System.out.println("value of atom " + ga + " corresponding to non-boolean function is " + (tv ? "true" : "false"));
				return tv;
			}
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}
