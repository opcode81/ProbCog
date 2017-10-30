/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
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
package probcog.hmm.latent;

import java.util.List;

import probcog.exception.ProbCogException;
import probcog.hmm.IObservationModel;
import probcog.hmm.Segment;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;
import edu.tum.cs.util.datastruct.ParameterMap;

/**
 * interface a sub-HMM of a hierarchical HMM must implement
 * @author Dominik Jain
 */
public interface ISubHMM extends IDwellTimeHMM<ObservationVector> {
	public void learn(List<? extends Segment<? extends ObservationVector>> s, ParameterMap learningParams) throws ProbCogException;
	public IObservationModel<ObservationVector> getForwardCalculator();
}
