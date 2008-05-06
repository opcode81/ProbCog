package edu.tum.cs.bayesnets.relational.core;

import java.util.Collection;
import java.util.Set;
import java.util.Vector;


public class ParameterGrounder {
	
	public static Collection<String[]> generateGroundings(RelationalNode node, Database db) throws Exception {
		Vector<String[]> ret = new Vector<String[]>();
		generateGroundings(ret, db, node, new String[node.params.length], node.getSignature().argTypes, 0);
		return ret;
	}
	
	private static void generateGroundings(Collection<String[]> ret, Database db, RelationalNode node, String[] params, String[] domainNames, int i) throws Exception {
		// if we have the full set of parameters, count the example
		if(i == domainNames.length) {
			ret.add(params.clone());
			return;
		}
		// otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
		Set<String> domain = db.getDomain(domainNames[i]);		
		if(domain == null)
			throw new Exception("Domain " + domainNames[i] + " not found in the database!");
		for(String element : domain) {
			params[i] = element;
			generateGroundings(ret, db, node, params, domainNames, i+1);	
		}		
	}
}
