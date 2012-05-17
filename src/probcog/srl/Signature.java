/**
 * 
 */
package probcog.srl;

import probcog.srl.directed.RelationalNode;
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

	/**
	 * whether the function is a utility function
	 */
	public boolean isUtility;

	public Signature(String functionName, String returnType, String[] argTypes, boolean isLogical, boolean isUtility) {
		this.returnType = returnType;
		this.argTypes = argTypes;
		this.functionName = functionName;
		this.isLogical = isLogical;
		this.isUtility = isUtility;
	}
	
	public Signature(String functionName, String returnType, String[] argTypes) {
		this(functionName, returnType, argTypes, false, false);
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
		return BooleanDomain.isBooleanType(returnType);
	}
	
	public boolean isReal() {
		return RealDomain.isRealType(returnType);
	}
	
	@Override
	public String toString() {
		return String.format("%s %s %s %s(%s)", isLogical ? "logical" : "non-logical", isUtility ? "utility" : "non-utility", returnType, functionName, StringTool.join(",", argTypes));
	}

	public static String formatVarName(String functionName, String[] args) {
		return String.format("%s(%s)", functionName, StringTool.join(",", args));
	}
	
	public static Pair<String, String[]> parseVarName(String varName) {
		return RelationalNode.parse(varName);
	}
}