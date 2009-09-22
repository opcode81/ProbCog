/*
 * Created on Sep 21, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.taxonomy;

import java.util.Vector;

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
	
	public Vector<Concept> getDescendants() {
		Vector<Concept> ret = new Vector<Concept>();
		ret.add(this);
		for(int i = 0; i < ret.size(); i++) {
			Concept c = ret.get(i);
			ret.addAll(c.children);			
		}
		return ret;
	}
	
	public Vector<Concept> getParents() {
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
