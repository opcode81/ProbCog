package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import edu.tum.cs.srl.Database;
import edu.tum.cs.tools.StringTool;

public class Exist extends UngroundedFormula {
	Formula f;
	Collection<String> vars;
	/**
	 * maps the quantified variables to their domain names
	 */
	Map<String, String> var2domName;
	
	public Exist(Collection<String> vars, Formula f) {
		this.vars = vars;
		this.f = f;
		this.var2domName = new HashMap<String, String>();
	}
	
	public String toString() {
		return "EXIST " + StringTool.join(",", vars) + " (" + f.toString() + ")";
	}

	@Override
	public void getVariables(Database db, Map<String, String> ret) {
		f.getVariables(db, ret);
		for(String var : vars) {
			var2domName.put(var, ret.remove(var));
		}
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables worldVars, Database db) throws Exception {
		// check if the domains of the quantified variables have been determined, and obtain them if necessary
		if(var2domName.size() < vars.size()) {			
			this.getVariables(db, new HashMap<String, String>());
		}
		// ground
		Vector<Formula> disjuncts = new Vector<Formula>();
		f.generateGroundings(disjuncts, db, binding, vars.toArray(new String[vars.size()]), 0, var2domName, worldVars, false);
		return new Disjunction(disjuncts);
	}

	@Override
	public Formula toCNF() {
		throw new RuntimeException("Cannot convert ungrounded formula to CNF.");
	}
}
