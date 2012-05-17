/*
 * Created on Nov 3, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.logic.sat;

import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.srl.AbstractVariable;

public class WalkSAT extends SampleSAT {

	public WalkSAT(Iterable<? extends probcog.logic.sat.Clause> kb, PossibleWorld state, WorldVariables vars, Iterable<? extends AbstractVariable<?>> db) throws Exception {
		super(kb, state, vars, db);
	}

	@Override
	protected void makeMove() {
		walkSATMove();
	}
}
