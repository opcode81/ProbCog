package edu.tum.cs.srl.bayesnets.bln.coupling;

import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;

/**
 * couples the logical variables (ground atoms) with the actual variables (belief nodes)
 * @author jain
 *
 */
public interface IVariableLogicCoupler {
	public int getValue(PossibleWorld w);		
	public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars);
}