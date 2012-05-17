package probcog.srl.directed.bln.coupling;

import probcog.logic.GroundLiteral;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;

/**
 * couples the logical variables (ground atoms) with the actual variables (belief nodes)
 * @author jain
 *
 */
public interface IVariableLogicCoupler {
	public int getValue(PossibleWorld w);		
	public void setValue(PossibleWorld w, int domIdx);
	public GroundLiteral getGroundLiteral(int domIdx, WorldVariables worldVars);
	public Iterable<String> getOriginalParams();
}