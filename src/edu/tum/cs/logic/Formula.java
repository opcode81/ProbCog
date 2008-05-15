package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import edu.tum.cs.bayesnets.relational.core.Database;

public abstract class Formula {	
	public abstract void getVariables(Database db, HashMap<String,String> ret);
	public abstract Formula ground(HashMap<String, String> binding, WorldVariables worldVars, Database db) throws Exception;
	public abstract void getGroundAtoms(Set<GroundAtom> ret);
	public abstract boolean isTrue(PossibleWorld w);
	
	public Vector<Formula> getAllGroundings(Database db, WorldVariables worldVars) throws Exception {
		Vector<Formula> ret = new Vector<Formula>();
		addAllGroundingsTo(ret, db, worldVars);
		return ret;
	}
	
	/**
	 * generates all groundings and adds them to the given collection
	 * @param collection
	 * @param db
	 * @param worldVars
	 * @throws Exception
	 */
	public void addAllGroundingsTo(Collection<Formula> collection, Database db, WorldVariables worldVars) throws Exception {
		HashMap<String, String> vars = new HashMap<String, String>();
		getVariables(db, vars);
		String[] varNames = vars.keySet().toArray(new String[vars.size()]);
		generateGroundings(collection, db, new HashMap<String, String>(), varNames, 0, vars, worldVars);		
	}
	
	protected void generateGroundings(Collection<Formula> ret, Database db, HashMap<String, String> binding, String[] varNames, int i, HashMap<String, String> var2domName, WorldVariables worldVars) throws Exception {
		// if we have the full set of parameters, add it to the collection
		if(i == varNames.length) {
			ret.add(this.ground(binding, worldVars, db));
			return;
		}
		// otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
		String varName = varNames[i];
		String domName = var2domName.get(varName);
		Set<String> domain = db.getDomain(domName);		
		if(domain == null)
			throw new Exception("Domain " + domName + " not found in the database!");
		for(String element : domain) {
			binding.put(varName, element);
			generateGroundings(ret, db, binding, varNames, i+1, var2domName, worldVars);	
		}		
	}
}
