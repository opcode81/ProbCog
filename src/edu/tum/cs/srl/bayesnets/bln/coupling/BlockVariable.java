/*
 * Created on Oct 27, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.bln.coupling;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;

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
}