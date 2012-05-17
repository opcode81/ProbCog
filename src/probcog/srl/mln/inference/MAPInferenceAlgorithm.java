/*
 * Created on Aug 20, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl.mln.inference;

import probcog.logic.IPossibleWorld;
import probcog.srl.mln.MarkovRandomField;

public abstract class MAPInferenceAlgorithm extends InferenceAlgorithm {

	public MAPInferenceAlgorithm(MarkovRandomField mrf) throws Exception {
		super(mrf);
	}

	/**
	 * gets the most likely state found by the algorithm
	 * @return the most likely possible world
	 */
	public abstract IPossibleWorld getSolution();
}
