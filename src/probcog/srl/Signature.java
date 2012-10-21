/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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

import probcog.srl.directed.RelationalNode;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;

/**
 * Represents the signature of a function/predicate appearing in a relational model.
 * @author Dominik Jain
 */
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