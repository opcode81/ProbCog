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

import java.util.Vector;

/**
 * Represents a concept in a taxonomy.
 * @author Dominik Jain
 */
public class Concept {
	public String name;
	public Concept parent = null;
	public Vector<Concept> children = new java.util.Vector<Concept>();	
	
	public Concept(String name) {
		this.name = name;	
	}
	
	public int hashCode() {
		return name.hashCode();
	}
	
	public boolean equals(Object other) {
		if(other instanceof Concept)
			return name.equals(((Concept)other).name);
		return false;		
	}
	
	/**
	 * declares that this concept is a c 
	 * @param parent
	 */
	public void setParent(Concept c) {
		this.parent = c;
		c.children.add(this);
	}	
	
	/**
	 * gets the list of descendants/sub-concepts of the concept
	 * @return
	 */
	public Vector<Concept> getDescendants() {
		Vector<Concept> ret = new Vector<Concept>();
		ret.add(this);
		for(int i = 0; i < ret.size(); i++) {
			Concept c = ret.get(i);
			ret.addAll(c.children);			
		}
		return ret;
	}
	
	/**
	 * gets the list of ancestors/super-concepts of the concept
	 * @return list (sorted in increasing order of generality)
	 */
	public Vector<Concept> getAncestors() {
		Vector<Concept> ret = new Vector<Concept>();
		Concept parent = this.parent;
		while(parent != null) {
			ret.add(parent);
			parent = parent.parent;
		}
		return ret;
	}
	
	public String toString() {
		return name;
	}
}
