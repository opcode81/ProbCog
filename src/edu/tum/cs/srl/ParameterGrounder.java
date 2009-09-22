package edu.tum.cs.srl;

import java.util.Collection;
import java.util.Set;
import java.util.Vector;

import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;

/**
 * generates groundings (instantiations of the parameters) of a function/predicate given a database
 * @author jain
 *
 */
public class ParameterGrounder {
	/**
	 * generates all groundings of the given node using the domain elements specified in the given database
	 * @param node
	 * @param db
	 * @return a collection of possible parameter bindings
	 * @throws Exception 
	 */
	@Deprecated
	public static Collection<String[]> generateGroundings(RelationalNode node, Database db) throws Exception {
		return generateGroundings(db, node.getSignature().argTypes);
	}

	/**
	 * generates all groundings of the given function using the domain elements specified in the given database
	 * @param node
	 * @param db
	 * @return a collection of possible parameter bindings
	 * @throws Exception 
	 */
	public static Collection<String[]> generateGroundings(Signature sig, Database db) throws Exception {
		return generateGroundings(db, sig.argTypes);	
	}
	
	/**
	 * generates all groundings of the given function using the domain elements specified in the given database
	 * @param rbn		relational belief network defining the signature of the function
	 * @param function	the name of the function
	 * @param db		
	 * @return a collection of possible parameter bindings
	 * @throws Exception
	 */
	public static Collection<String[]> generateGroundings(RelationalModel model, String function, Database db) throws Exception {
		try {
			return generateGroundings(db, model.getSignature(function).argTypes);
		}
		catch(Exception e) {
			System.err.println("Warning: " + e.getMessage() + " (while grounding '" + function + "')");
			return new Vector<String[]>();
		}
	}
	
	public static Collection<String[]> generateGroundings(Database db, String[] domainNames) throws Exception {
		Vector<String[]> ret = new Vector<String[]>();
		generateGroundings(ret, db, new String[domainNames.length], domainNames, 0);
		return ret;
	}
	
	private static void generateGroundings(Collection<String[]> ret, Database db, String[] params, String[] domainNames, int i) throws Exception {
		// if we have the full set of parameters, add it to the collection
		if(i == domainNames.length) {
			ret.add(params.clone());
			return;
		}
		// otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
		Iterable<String> domain = db.getDomain(domainNames[i]);		
		if(domain == null)
			throw new Exception("Domain " + domainNames[i] + " not found in the database!");
		for(String element : domain) {
			params[i] = element;
			generateGroundings(ret, db, params, domainNames, i+1);	
		}		
	}
}
