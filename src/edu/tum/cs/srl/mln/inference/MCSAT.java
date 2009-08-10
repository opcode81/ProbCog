/*
 * Created on Aug 7, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.mln.inference;

import java.util.Vector;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.sat.weighted.WeightedClausalKB;
import edu.tum.cs.srl.mln.MarkovRandomField;

public class MCSAT extends InferenceAlgorithm {

	edu.tum.cs.logic.sat.weighted.MCSAT sampler;
	
	public MCSAT(MarkovRandomField mrf) throws Exception {
		super(mrf);
		WeightedClausalKB wckb = new WeightedClausalKB(mrf);
		sampler = new edu.tum.cs.logic.sat.weighted.MCSAT(wckb, mrf.getWorldVariables(), mrf.getDb());
	}

	@Override
	public double getResult(GroundAtom ga) {
		// TODO Auto-generated method stub
		// use datastruct for results
		return 0;
	}

	@Override
	public Vector<InferenceResult> infer(Iterable<String> queries, int maxSteps)
			throws Exception {
		sampler.run(maxSteps);
		return getResults(queries);
	}

}
