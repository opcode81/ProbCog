/*
 * Created on Nov 3, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.logic.sat;

import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.srl.AbstractVariable;

public class WalkSAT extends SampleSAT {

	public WalkSAT(Iterable<? extends edu.tum.cs.logic.sat.Clause> kb, PossibleWorld state, WorldVariables vars, Iterable<? extends AbstractVariable<?>> db) throws Exception {
		super(kb, state, vars, db);
	}

	@Override
	protected void makeMove() {
		walkSATMove();
	}
}
