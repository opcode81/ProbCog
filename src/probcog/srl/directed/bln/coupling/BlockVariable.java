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
import probcog.logic.WorldVariables.Block;
import edu.tum.cs.util.datastruct.ArraySlice;

/**
 * Represents the coupling of a block of mutually exclusive logical variables.
 * @author Dominik Jain
 */
public class BlockVariable implements IVariableLogicCoupler {
	protected Block block;
	
	public BlockVariable(Block b) {
		block = b;
	}
	
	public int getValue(PossibleWorld w) {
		int i = 0;
		for(GroundAtom ga : block) {
			if(ga.isTrue(w))
				return i;
			++i;
		}
		throw new RuntimeException("No true atom in block " + block);
	}

	public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars) {
		GroundAtom ga = block.get(domIdx);
		return new GroundLiteral(true, ga);
	}

	public void setValue(PossibleWorld w, int domIdx) {
		int i = 0;
		for(GroundAtom ga : block) {
			w.set(ga, domIdx == i);
			++i;
		}
	}	
	
	public Iterable<String> getOriginalParams() {
		return new ArraySlice<String>(block.get(0).args, 0, -1);
	}
}