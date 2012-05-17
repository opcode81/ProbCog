/*
 * Created on Oct 15, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.bayesnets.inference;

import probcog.bayesnets.core.BeliefNetworkEx;

/**
 * wrapper for BNJ's Variable Elimination (exact inference)
 * @author jain
 */
public class BNJVariableElimination extends BNJInference {

	public BNJVariableElimination(BeliefNetworkEx bn) throws Exception {
		super(bn, edu.ksu.cis.bnj.ver3.inference.exact.Elimbel.class);
	}

}
