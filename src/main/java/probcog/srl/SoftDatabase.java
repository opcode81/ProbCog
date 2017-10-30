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

import probcog.exception.ProbCogException;

// TODO doesn't consider Prolog
/**
 * Represents a training database with soft data where the values of variables
 * are distributions.
 * @author Dominik Jain
 */
public class SoftDatabase extends GenericDatabase<SoftVariable, ValueDistribution> {

	public SoftDatabase(RelationalModel model) throws ProbCogException {
		super(model);
	}

	@Override
	public ValueDistribution getVariableValue(String varName, boolean closedWorld) throws ProbCogException {
		return this.getVariable(varName).value;
	}

	@Override
	public void fillDomain(String domName, SoftVariable var) throws ProbCogException {
		for(String v : var.value.getDomainElements()) {
			fillDomain(domName, v);
		}
	}

	@Override
	protected SoftVariable makeVar(String functionName, String[] args, String value) {
		ValueDistribution vd = new ValueDistribution();
		vd.setValue(value, 1.0);
		return new SoftVariable(functionName, args, vd);
	}

	@Override
	protected SoftVariable readEntry(String line) throws ProbCogException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getSingleVariableValue(String varName, boolean closedWorld) throws ProbCogException {
		ValueDistribution vd = getVariableValue(varName, false);
		return vd.getSingleValue();
	}
}
