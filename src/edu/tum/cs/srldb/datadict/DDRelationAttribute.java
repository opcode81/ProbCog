package edu.tum.cs.srldb.datadict;

import edu.tum.cs.srldb.datadict.domain.*;

/**
 * represents a boolean attribute of a relation/link 
 * 
 * @author Dominik Jain
 *
 */
public class DDRelationAttribute extends DDAttribute {
	private static final long serialVersionUID = 1L;
	public boolean[] singleVal;
	
	/**
	 * constructs an attribute object that inherits the single value array of the relation/link it is added to
	 * @param name the name of the attribute
	 */
	public DDRelationAttribute(String name) {		
		this(name, null);
	}

	/**
	 * constructs a relation attribute that overrides the parent relation's single value array 
	 * @param name the name of the attribute
	 * @param singleVal an array of boolean values that is meant to override the array of the relation object that this attribute is added to -- for the predicate that is generated from this attribute
	 */
	public DDRelationAttribute(String name, boolean[] singleVal) {
		super(name, BooleanDomain.getInstance());
		this.singleVal = singleVal;
	}
}
