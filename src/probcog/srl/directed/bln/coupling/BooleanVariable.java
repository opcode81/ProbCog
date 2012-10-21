/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
package probcog.srl.directed.bln.coupling;

import probcog.logic.GroundAtom;
import probcog.logic.GroundLiteral;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import edu.tum.cs.util.datastruct.ArraySlice;

/**
 * Represents the coupling of a Boolean variable.
 * @author Dominik Jain
 */
public class BooleanVariable implements IVariableLogicCoupler {
	protected GroundAtom ga;
	
	public BooleanVariable(GroundAtom ga) {
		this.ga = ga;
	}
	
	public int getValue(PossibleWorld w) {			
		return w.get(ga.index) ? 0 : 1; // True is first element
	}
	
	public void setValue(PossibleWorld w, int domIdx) {
		w.set(ga.index, domIdx == 0);
	}

	public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars) {
		return new GroundLiteral(domIdx == 0, ga);
	}
	
	public Iterable<String> getOriginalParams() {
		return new ArraySlice<String>(ga.args, 0);
	}
}