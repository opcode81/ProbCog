/*
 * Created on Sep 21, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.taxonomy;

import java.util.HashMap;
import java.util.Vector;

public class Taxonomy {
	protected HashMap<String,Concept> concepts = new HashMap<String, Concept>();
	
	public Taxonomy() {
	}
	
	public void addConcept(Concept c) {
		concepts.put(c.name, c);
	}
	
	public Concept getConcept(String name) {
		return concepts.get(name);
	}
	
	/**
	 * gets the list of all concepts that are descendants of the concept with the given name (including the concept itself)
	 * @param conceptName
	 * @return
	 * @throws Exception 
	 */
	public Vector<Concept> getDescendants(String conceptName) throws Exception {
		Concept c = getConcept(conceptName);
		if(c == null)
			throw new Exception("Concept '" + conceptName + "' not in taxonomy.");
		return c.getDescendants();
	}
	
	public boolean query_isa(String subtype, String type) {
		Concept c = getConcept(type);
		Vector<Concept> p1 = getConcept(subtype).getParents();
		Vector<Concept> p2 = c.getParents();
		if(!(p1.size() > p2.size()))
				return false;
		return p1.contains(c);			
	}
}
