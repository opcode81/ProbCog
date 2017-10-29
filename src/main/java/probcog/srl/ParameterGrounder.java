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
package probcog.srl;

import java.util.Collection;
import java.util.Vector;

import probcog.srl.directed.RelationalNode;


/**
 * Helper class for the generation of groundings (instantiations of the parameters) 
 * of a function/predicate given a database.
 * @author Dominik Jain
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
	public static Collection<String[]> generateGroundings(Signature sig, GenericDatabase<?,?> db) throws Exception {
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
	
	public static Collection<String[]> generateGroundings(GenericDatabase<?,?> db, String[] domainNames) throws Exception {
		Vector<String[]> ret = new Vector<String[]>();
		generateGroundings(ret, db, new String[domainNames.length], domainNames, 0);
		return ret;
	}
	
	// TODO it would be better to use an iterator that generates groundings as we go along rather than putting them all into one big collection
	private static void generateGroundings(Collection<String[]> ret, GenericDatabase<?,?> db, String[] params, String[] domainNames, int i) throws Exception {
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
