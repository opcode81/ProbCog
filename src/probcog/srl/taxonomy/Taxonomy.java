/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
package probcog.srl.taxonomy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;

/**
 * Represents a simple taxonomy of concepts.
 * @author Dominik Jain
 */
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
