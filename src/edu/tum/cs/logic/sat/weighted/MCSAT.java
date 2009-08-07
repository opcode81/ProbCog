/*
 * Created on Aug 7, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.logic.sat.weighted;

import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;

public class MCSAT {

	PossibleWorld state;
	
	public MCSAT(Iterable<? extends WeightedClause> kb, WorldVariables vars) {
		state = new PossibleWorld(vars);
	}
	
	public run(int maxSteps) {
		
	}
	
}
