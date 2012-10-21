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

import edu.tum.cs.util.StringTool;

/**
 * Abstract base class for variables appearing in relational databases. 
 * @author Dominik Jain
 *
 * @param <ValueType> the type of the value.
 */
public abstract class AbstractVariable<ValueType> {
	/**
	 * the node name or function/predicate name
	 */
	public String functionName;
	/**
	 * the actual parameters of the function/predicate
	 */
	public String[] params;
	public ValueType value;
	
	public AbstractVariable(String functionName, String[] params, ValueType value) {
		this.functionName = functionName;
		this.params = params;
		this.value = value;
	}
	
	public String toString() {
		return getKeyString() + " = " + value;			
	}
	
	public String getKeyString() {
		return functionName + "(" + StringTool.join(",", params) + ")";
	}
	
	public abstract boolean isTrue();
	
	/**
	 * gets the predicate representation that corresponds to the assignment of this variable, i.e. for a(x)=v, return a(x,v) 
	 * @return
	 */
	public abstract String getPredicate();

	public abstract boolean isBoolean();
	
	public abstract ValueType getValue();
	
	public abstract boolean hasValue(String value);
	
	public abstract boolean pertainsToEvidenceFunction(); 
}