package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.tools.StringTool;

public class Exist extends Formula {
	Formula f;
	Collection<String> vars;
	/**
	 * maps the quantified variables to their domain names
	 */
	HashMap<String, String> var2domName;
	
	public Exist(Collection<String> vars, Formula f) {
		this.vars = vars;
		this.f = f;
		this.var2domName = new HashMap<String, String>();
	}
	
	public String toString() {
		return "EXIST " + StringTool.join(",", vars) + " (" + f.toString() + ")";
	}

	@Override
	public void getVariables(Database db, HashMap<String, String> ret) {
		f.getVariables(db, ret);
		for(String var : vars) {
			var2domName.put(var, ret.remove(var));
		}
	}

	@Override
	public Formula ground(HashMap<String, String> binding, WorldVariables worldVars, Database db) throws Exception {
		Vector<Formula> disjuncts = new Vector<Formula>();
		f.generateGroundings(disjuncts, db, binding, vars.toArray(new String[vars.size()]), 0, var2domName, worldVars);
		return new Disjunction(disjuncts);
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
	}

	@Override
	public boolean isTrue(PossibleWorld w) {
		throw new RuntimeException("not supported");
	}
}
