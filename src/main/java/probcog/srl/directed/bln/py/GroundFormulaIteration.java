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
package probcog.srl.directed.bln.py;

import java.util.Iterator;

import org.python.core.PyObject.ConversionException;


public class GroundFormulaIteration implements Iterator<GroundFormula>, Iterable<GroundFormula> {

	protected BayesianLogicNetworkPy bln;
	protected int i, count;
	
	public GroundFormulaIteration(BayesianLogicNetworkPy bln) throws ConversionException {
		this.bln = bln;
		i = 0;
		count = bln.jython.evalInt("len(mln.gndFormulas)");
	}
	
	public boolean hasNext() {
		return i < count;
	}

	public GroundFormula next() {
		try {
			return new GroundFormula(bln.jython, i++);
		}
		catch (ConversionException e) {
			throw new RuntimeException(e.getMessage());
		}			
	}

	public void remove() {
		throw new RuntimeException("Remove is not supported by this iterator.");
	}

	public Iterator<GroundFormula> iterator() {			
		return this;
	}		
	
	/**
	 * gets the number of objects in this iteration
	 * @return
	 */
	public int getCount() {
		return count;
	}
}
