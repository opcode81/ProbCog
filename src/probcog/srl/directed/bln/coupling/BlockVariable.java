/*
 * Created on Oct 27, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl.directed.bln.coupling;

import probcog.logic.GroundAtom;
import probcog.logic.GroundLiteral;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.logic.WorldVariables.Block;
import edu.tum.cs.util.datastruct.ArraySlice;

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