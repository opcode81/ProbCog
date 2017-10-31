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
import probcog.logic.GroundAtom;
import probcog.logic.sat.weighted.WeightedClausalKB;
import probcog.srl.mln.MarkovRandomField;

/**
 * MC-SAT inference wrapper.
 * @author Dominik Jain
 */
public class MCSAT extends InferenceAlgorithm {

	protected probcog.logic.sat.weighted.MCSAT sampler;
	protected int maxSteps = 5000;
	
	public MCSAT(MarkovRandomField mrf) throws ProbCogException {
		super(mrf);
		WeightedClausalKB wckb = new WeightedClausalKB(mrf, true);
		sampler = new probcog.logic.sat.weighted.MCSAT(wckb, mrf.getWorldVariables(), mrf.getDb());
		paramHandler.add("maxSteps", "setMaxSteps");
		paramHandler.addSubhandler(sampler.getParameterHandler());
	}
	
	public void setMaxSteps(int maxSteps) {
		this.maxSteps = maxSteps;
	}

	@Override
	public double getResult(GroundAtom ga) {
		return sampler.getResult(ga);
	}

	@Override
	protected void infer() throws ProbCogException {
		sampler.setDebugMode(debug);
		sampler.run(maxSteps);
	}
	
	public String getAlgorithmName() {
		return sampler.getAlgorithmName();
	}
}
