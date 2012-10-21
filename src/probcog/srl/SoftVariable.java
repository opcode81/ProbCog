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
 * Represents a variable for a soft database, where the values are probability distributions.
 * @author Dominik Jain
 */
public class SoftVariable extends AbstractVariable<ValueDistribution> {

	public SoftVariable(String functionName, String[] params, ValueDistribution value) {
		super(functionName, params, value);
	}
	
	@Override
	public boolean isTrue() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getPredicate() {
		throw new RuntimeException("impossible");
	}

	@Override
	public boolean isBoolean() {
		return false;
	}

	@Override
	public ValueDistribution getValue() {
		return value;
	}
	
	public double getValue(String domElement) {
		return value.getValue(domElement);
	}

	@Override
	public boolean hasValue(String value) {
		String singleVal = this.value.getSingleValue();
		if(singleVal == null)
			return false;
		return value.equalsIgnoreCase(singleVal);
	}

	@Override
	public boolean pertainsToEvidenceFunction() {
		return false;
	}
}
