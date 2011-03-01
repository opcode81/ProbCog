/**
 * 
 */
package edu.tum.cs.srl;

import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;

public class Signature {
	public String returnType;
	public String[] argTypes;
	public String functionName;
	/**
	 * whether the function is a strictly logically handled predicate (i.e. computed prior to probabilistic inference on logical grounds)
	 */
	public boolean isLogical;

	public Signature(String functionName, String returnType, String[] argTypes, boolean isLogical) {
		this.returnType = returnType;
		this.argTypes = argTypes;
		this.functionName = functionName;
		this.isLogical = isLogical;
	}
	
	public Signature(String functionName, String returnType, String[] argTypes) {
		this(functionName, returnType, argTypes, false);
	}
	
	public void replaceType(String oldType, String newType) {
		if(this.returnType.equals(oldType))
			this.returnType = newType;
		for(int i = 0; i < argTypes.length; i++) {
			if(argTypes[i].equals(oldType))
				argTypes[i] = newType;
		}
	}
	
	public boolean isBoolean() {
		return returnType.equalsIgnoreCase("boolean");
	}
	
	@Override
	public String toString() {
		return String.format("%s %s(%s)", returnType, functionName, StringTool.join(",", argTypes));
	}

	public static String formatVarName(String functionName, String[] args) {
		return String.format("%s(%s)", functionName, StringTool.join(",", args));
	}
	
	public static Pair<String, String[]> parseVarName(String varName) {
		return RelationalNode.parse(varName);
	}
}