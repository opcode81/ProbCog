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

public class BooleanVariable implements IVariableLogicCoupler {
	public int idxGndAtom;
	
	public BooleanVariable(GroundAtom ga) {
		this.idxGndAtom = ga.index;
	}
	
	public int getValue(PossibleWorld w) {			
		return w.get(idxGndAtom) ? 0 : 1; // True is first element
	}

	public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars) {
		GroundAtom ga = worldVars.get(idxGndAtom);
		return new GroundLiteral(domIdx == 0, ga);
	}		
}