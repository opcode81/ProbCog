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

/**
 * Represents a basic string-valued variable.
 * @author Dominik Jain
 */
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
