/*
 * Created on Sep 21, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl.taxonomy;

import java.util.Collection;
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
	
	public Collection<Concept> getConcepts() {
		return concepts.values();
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
	
	public boolean query_isa(String subtype, String type) throws Exception {
		Concept c = getConcept(type);
		Concept sc = getConcept(subtype);
		if(c == null)
			throw new Exception("Concept '" + type + "' unknown.");
		if(sc == null)
			throw new Exception("Concept '" + subtype + "' unknown.");
		Vector<Concept> p1 = sc.getAncestors();		
		Vector<Concept> p2 = c.getAncestors();
		if(!(p1.size() > p2.size()))
				return false;
		return p1.contains(c);			
	}
}
