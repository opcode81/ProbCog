/*
 * Created on Aug 20, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.mln.inference;

import edu.tum.cs.logic.IPossibleWorld;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.srl.mln.MarkovRandomField;

public abstract class MAPInferenceAlgorithm extends InferenceAlgorithm {

	public MAPInferenceAlgorithm(MarkovRandomField mrf) {
		super(mrf);
	}

	/**
	 * gets the most likely state found by the algorithm
	 * @return the most likely possible world
	 */
	public abstract IPossibleWorld getSolution();
}
