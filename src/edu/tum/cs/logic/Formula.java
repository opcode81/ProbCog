package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.tum.cs.logic.parser.FormulaParser;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.GenericDatabase;
import edu.tum.cs.srl.RelationalModel;

public abstract class Formula {	
	/**
	 * gets a mapping from names of meta-variables appearing in the formula to the types/domains they apply to
	 * @param db 
	 * @param ret mapping in which to store the result
	 * @throws Exception
	 */
	public abstract void getVariables(GenericDatabase<?, ?> db, Map<String,String> ret) throws Exception;
	/**
	 * adds any constants appearing in the formula to the given relational model
	 * @param m
	 */
	public abstract void addConstantsToModel(RelationalModel m) throws Exception;
	/**
	 * grounds this formula for a particular binding of its variables
	 * @param binding		the variable binding
	 * @param worldVars		the set of ground atoms (which is needed to return the ground versions of atoms)
	 * @param db			a database containing a set of constants for each type that can be used to ground quantified formulas (can be null if the formula does not contain any quantified variables)
	 * @return
	 * @throws Exception
	 */
	public abstract Formula ground(Map<String, String> binding, WorldVariables worldVars, GenericDatabase<?, ?> db) throws Exception;
	/**
	 * gets the set of ground atoms appearing in this (grounded) formula
	 * @param ret the set to write to
	 */
	public abstract void getGroundAtoms(Set<GroundAtom> ret);
	public abstract boolean isTrue(IPossibleWorld w);

	/**
	 * gets a list of all groundings of the formula for a particular set of objects
	 * @param db  the database containing all relevant constant symbols (objects) to use for grounding
	 * @param worldVars  the collection of variables (ground atoms) that defines the set of possible worlds
	 * @param simplify whether to use the evidence in the database to simplify ground formulas
	 * @return
	 * @throws Exception
	 */
	public Vector<Formula> getAllGroundings(Database db, WorldVariables worldVars, boolean simplify) throws Exception {
		Vector<Formula> ret = new Vector<Formula>();
		addAllGroundingsTo(ret, db, worldVars, simplify);
		return ret;
	}

	/**
	 * generates all groundings and adds them to the given collection
	 * @param collection
	 * @param db
	 * @param worldVars
	 * @param simplify whether to use the evidence in the database to simplify ground formulas
	 * @throws Exception
	 */
	public void addAllGroundingsTo(Collection<Formula> collection, Database db, WorldVariables worldVars, boolean simplify) throws Exception {
		HashMap<String, String> vars = new HashMap<String, String>();
		getVariables(db, vars);
		String[] varNames = vars.keySet().toArray(new String[vars.size()]);
		generateGroundings(collection, db, new HashMap<String, String>(), varNames, 0, vars, worldVars, simplify);
	}

	/**
	 * recursively generates groundings of the formula
	 * @param ret  the collection in which to store the generated groundings
	 * @param db  the database in which all usable constant symbols are found
	 * @param binding  a mapping of variable names to constant names
	 * @param varNames  the variables that are to be grounded
	 * @param i  the current index of the variable in varNames to ground next
	 * @param var2domName  a mapping of variable names to domain names (that contains as keys at least the variables in varNames)
	 * @param worldVars  the collection of variables (ground atoms) that defines the set of possible worlds
	 * @param simplify whether to use the evidence in the database to simplify ground formulas
	 * @throws Exception
	 */
	protected void generateGroundings(Collection<Formula> ret, GenericDatabase<?,?> db, Map<String, String> binding, String[] varNames, int i, Map<String, String> var2domName, WorldVariables worldVars, boolean simplify) throws Exception {
		// if we have the full set of parameters, add it to the collection
		if(i == varNames.length) {
            Formula f = (this.ground(binding, worldVars, db));
            if(simplify)
            	f = f.simplify(db);
            if(f instanceof TrueFalse) {
            	if(!((TrueFalse)f).isTrue())
            		// TODO this may be an error if the formula that was grounded was "hard"
            		;//throw new Exception("Unsatisfiable formula encountered: " + this.toString() + " with binding " + binding.toString() + " cannot be satisfied (given the evidence).");            		
            	return;
            }
            else
                ret.add(f);
			return;
		}
		// otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
		String varName = varNames[i];
		String domName = var2domName.get(varName);
		Iterable<String> domain = db.getDomain(domName);
		if(domain == null)
			throw new Exception("Domain named '" + domName + "' (of variable " + varName + " in formula " + this.toString() + ") not found in the database!");
		for(String element : domain) {
			binding.put(varName, element);
			generateGroundings(ret, db, binding, varNames, i+1, var2domName, worldVars, simplify);
		}
	}

	public abstract Formula toCNF();
	
	public abstract Formula toNNF();

	/**
	 * simplifies the formula by removing parts of the formula that can be evaluated 
	 * @param evidence an evidence database with which to evaluate ground atoms (may be null; if null, will only simplify based on TrueFalse instances appearing within the formula)
	 * @return a simplified version of the formula that incorporates the evidence 
	 */
    public abstract Formula simplify(GenericDatabase<?, ?> evidence);

	public static Formula fromString(String f) throws ParseException {
		return FormulaParser.parse(f);
	}

	public static void main(String[] args) throws ParseException {
		String s = "a(x) <=> b(x)";
		//s = "a(x) v (b(x) ^ c(x))";
		//s = "(a(x) v (b(x) ^ c(x))) => f(x)";
		s = "(a(x) ^ b(x) ^ !c(x) ^ !d(x)) v (a(x) ^ !b(x) ^ c(x) ^ !d(x)) v (!a(x) ^ b(x) ^ c(x) ^ !d(x)) v (a(x) ^ !b(x) ^ !c(x) ^ d(x)) v (!a(x) ^ b(x) ^ !c(x) ^ d(x)) v (!a(x) ^ !b(x) ^ c(x) ^ d(x))";
		Formula f = fromString(s);
		System.out.println("CNF: " + f.toCNF().toString());
	}
}
