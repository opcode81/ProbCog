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
package probcog.hmm;

import java.util.Collection;

import probcog.exception.ProbCogException;

/**
 * Interface for essential HMM functionality
 * @author Dominik Jain
*/
public interface IHMM<O> {
	public IObservationModel<O> getObservationModel(int state);
	public double getPi(int state);
	public Integer getNumStates();
	public void setNumStates(int numStates) throws ProbCogException;
	public void setA(double[][] A);
	public void setPi(double[] pi);
	public void learnObservationModel(int state, Collection<? extends Collection<? extends O>> data) throws ProbCogException;
}
