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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import probcog.exception.ProbCogException;
import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;

import edu.tum.cs.util.StringTool;

/**
 * Represents an existentiall quantified formula.
 * @author Dominik Jain
 */
public class Exist extends UngroundedFormula {
	public Formula f;
	public Collection<String> vars;
	/**
	 * maps the quantified variables to their domain names
	 */
	Map<String, String> var2domName;
	
	public Exist(Collection<String> vars, Formula f) {
		this.vars = vars;
		this.f = f;
		this.var2domName = new HashMap<String, String>();
	}
	
	public Exist(String[] vars, Formula f) {
		this(Arrays.asList(vars), f);
	}
	
	public String toString() {
		return "EXIST " + StringTool.join(",", vars) + " (" + f.toString() + ")";
	}

	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws ProbCogException {
		f.getVariables(db, ret);
		for(String var : vars) {
			var2domName.put(var, ret.remove(var));
		}
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) throws ProbCogException {
		f.addConstantsToModel(m);
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables worldVars, GenericDatabase<?, ?> db) throws ProbCogException {
		// check if the domains of the quantified variables have been determined, and obtain them if necessary
		if(var2domName.size() < vars.size()) {			
			this.getVariables(db, new HashMap<String, String>());
		}
		// ground
		Vector<Formula> disjuncts = new Vector<Formula>();
		f.generateGroundings(disjuncts, db, binding, vars.toArray(new String[vars.size()]), 0, var2domName, worldVars, FormulaSimplification.None);
		return new Disjunction(disjuncts);
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
