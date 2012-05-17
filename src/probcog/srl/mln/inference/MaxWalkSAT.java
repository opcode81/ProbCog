/*
 * Created on Aug 5, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl.mln.inference;

import java.util.ArrayList;

import probcog.logic.GroundAtom;
import probcog.logic.PossibleWorld;
import probcog.logic.sat.weighted.IMaxSAT;
import probcog.logic.sat.weighted.WeightedClausalKB;
import probcog.srl.mln.MarkovRandomField;


public class MaxWalkSAT extends MAPInferenceAlgorithm {
	
	protected IMaxSAT sat;
	
	public MaxWalkSAT(MarkovRandomField mrf) throws Exception {
		this(mrf, probcog.logic.sat.weighted.MaxWalkSAT.class);
	}
	
	public MaxWalkSAT(MarkovRandomField mrf, Class<? extends IMaxSAT> mwsClass) throws Exception {
		super(mrf);
        WeightedClausalKB wckb = new WeightedClausalKB(mrf, false);
        PossibleWorld state = new PossibleWorld(mrf.getWorldVariables());
        sat = mwsClass.getConstructor(WeightedClausalKB.class, PossibleWorld.class, probcog.logic.WorldVariables.class, probcog.srl.Database.class).newInstance(wckb, state, mrf.getWorldVariables(), mrf.getDb());
        //sat = new edu.tum.cs.logic.sat.weighted.MaxWalkSAT(wckb, state, mrf.getWorldVariables(), mrf.getDb());
	}
	
	@Override
	public double getResult(GroundAtom ga) {
		return sat.getBestState().get(ga.index) ? 1.0 : 0.0;
	}

	@Override
	public ArrayList<InferenceResult> infer(Iterable<String> queries) throws Exception {
        sat.setMaxSteps(maxSteps);
        sat.run();	        
		return getResults(queries);
	}

	public PossibleWorld getSolution() {
		return sat.getBestState();
	}

	@Override
	public String getAlgorithmName() {
		return String.format("MAP:%s", sat.getAlgorithmName());
	}
}
