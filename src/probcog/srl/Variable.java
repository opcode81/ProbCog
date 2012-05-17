/*
 * Created on Jan 31, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl;

import edu.tum.cs.util.StringTool;

public class Variable extends StringVariable {

	RelationalModel model;

	public Variable(String functionName, String[] params, String value, RelationalModel model) {
		super(functionName, params, value);
		this.model = model;
	}

	public String getPredicate() {
		if(isBoolean())
			return functionName + "(" + StringTool.join(",", params) + ")";
		else
			return functionName + "(" + StringTool.join(",", params) + "," + value + ")";
	}

	public boolean isBoolean() {
		return model.getSignature(functionName).isBoolean();
	}

	@Override
	public String toString() {
		return String.format("%s = %s", getName(), value);
	}
	
	/**
	 * @return the name of the variable
	 */
	public String getName() {
		return Signature.formatVarName(functionName, this.params); 
	}

	@Override
	public boolean pertainsToEvidenceFunction() {
		return model.getSignature(functionName).isLogical;
	}
}