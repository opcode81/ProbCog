package probcog.logic;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;

import edu.tum.cs.util.StringTool;

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
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws Exception {
		f.getVariables(db, ret);
		for(String var : vars) {
			var2domName.put(var, ret.remove(var));
		}
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) throws Exception {
		f.addConstantsToModel(m);
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables worldVars, GenericDatabase<?, ?> db) throws Exception {
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
