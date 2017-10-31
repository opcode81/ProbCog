/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.mln.inference;

import probcog.exception.ProbCogException;
import probcog.logic.IPossibleWorld;
import probcog.srl.mln.MarkovRandomField;

/**
 * Base class for inference methods that compute the most probable explanation (MPE) of the
 * evidence, i.e. the most probable possible world among the worlds satisfying the evidence.
 * 
 * @author Dominik Jain
 */
public abstract class MPEInferenceAlgorithm extends InferenceAlgorithm {

	public MPEInferenceAlgorithm(MarkovRandomField mrf) throws ProbCogException {
		super(mrf);
	}

	/**
	 * Gets the most probable possible world found by the algorithm
	 * @return the most probable possible world
	 */
	public abstract IPossibleWorld getSolution();
	
	/**
	 * Infers the most probable explanation of the evidence (MPE)
	 * @return the most probable possible world given the evidence
	 */
	public abstract IPossibleWorld inferMPE() throws ProbCogException;
	
	@Override
	protected void infer() throws ProbCogException {
		inferMPE();
	}
}
