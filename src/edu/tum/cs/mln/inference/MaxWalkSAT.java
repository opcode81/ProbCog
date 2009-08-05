/*
 * Created on Aug 5, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.mln.inference;

import java.util.Vector;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.sat.MAPMaxWalkSAT;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.srl.mln.WeightedClausalKB;

public class MaxWalkSAT extends InferenceAlgorithm {

	protected MAPMaxWalkSAT sat;
	
	public MaxWalkSAT(MarkovRandomField mrf) throws Exception {
		super(mrf);
        WeightedClausalKB wckb = new WeightedClausalKB(mrf);
        PossibleWorld state = new PossibleWorld(mrf.getWorldVariables());
        sat = new MAPMaxWalkSAT(wckb, state, mrf.getWorldVariables(), mrf.getDb(), mrf.mln.getMaxWeight());
	}
	
	@Override
	public double getResult(GroundAtom ga) {
		return sat.getBestState().get(ga.index) ? 1.0 : 0.0;
	}

	@Override
	public Vector<InferenceResult> infer(Iterable<String> queries, int maxSteps) throws Exception {
        sat.setMaxSteps(maxSteps);
        sat.run();	        
		return getResults(queries);
	}

}
