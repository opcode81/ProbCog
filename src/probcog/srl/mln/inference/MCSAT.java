/*
 * Created on Aug 7, 2009
 */
package probcog.srl.mln.inference;

import java.util.ArrayList;

import probcog.logic.GroundAtom;
import probcog.logic.sat.weighted.WeightedClausalKB;
import probcog.srl.mln.MarkovRandomField;


public class MCSAT extends InferenceAlgorithm {

	probcog.logic.sat.weighted.MCSAT sampler;
	
	public MCSAT(MarkovRandomField mrf) throws Exception {
		super(mrf);
		WeightedClausalKB wckb = new WeightedClausalKB(mrf, true);
		sampler = new probcog.logic.sat.weighted.MCSAT(wckb, mrf.getWorldVariables(), mrf.getDb());
		paramHandler.addSubhandler(sampler.getParameterHandler());
	}

	@Override
	public double getResult(GroundAtom ga) {
		return sampler.getResult(ga);
	}

	@Override
	public ArrayList<InferenceResult> infer(Iterable<String> queries) throws Exception {
		sampler.setDebugMode(debug);
		sampler.run(maxSteps);
		return getResults(queries);
	}
	
	public String getAlgorithmName() {
		return sampler.getAlgorithmName();
	}
}
