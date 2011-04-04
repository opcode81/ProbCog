/*
 * Created on Jan 31, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl;

public abstract class StringVariable extends AbstractVariable<String> {

	public StringVariable(String functionName, String[] params, String value) {
		super(functionName, params, value);
	}
	
	@Override
	public String getValue() {
		return this.value;
	}
	
	@Override
	public boolean isTrue() {
		return value.equalsIgnoreCase("True");
	}	

	@Override
	public boolean hasValue(String value) {
		return value.equalsIgnoreCase(this.value);
	}
}
