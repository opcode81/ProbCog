/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain.
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
 * @author Dominik Jain
 */
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