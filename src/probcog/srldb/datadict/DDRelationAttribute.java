/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
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
package probcog.srldb.datadict;

import probcog.srldb.datadict.domain.*;

/**
 * Represents a boolean attribute of a relation/link.
 * @author Dominik Jain
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
