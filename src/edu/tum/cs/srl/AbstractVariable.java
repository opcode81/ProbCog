/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl;

import edu.tum.cs.util.StringTool;

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
}