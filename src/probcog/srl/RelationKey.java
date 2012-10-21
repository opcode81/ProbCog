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
package probcog.srl;

import java.util.Vector;

import edu.tum.cs.util.StringTool;

/**
 * Represents a functional property of a relation.
 * @author Dominik Jain
 */
public class RelationKey {
	/**
	 * the name of the relation
	 */
	public String relation;
	/**
	 * list of indices of the parameters that make up a key
	 */
	public Vector<Integer> keyIndices;
	/**
	 * the original arguments with which the relation key was declared (i.e. list of parameters with "_" as entries for functionally determined arguments)
	 */
	protected String[] arguments;
	
	public RelationKey(String relation, String[] arguments) {
		this.relation = relation;
		this.arguments = arguments;
		keyIndices = new Vector<Integer>();
		for(int i = 0; i < arguments.length; i++) {
			if(!arguments[i].equals("_")) {
				keyIndices.add(i);
			}
		}
	}
	
	public String toString() {
		return "relationKey " + relation + "(" + StringTool.join(",", arguments) + ");";
	}
}