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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

import be.ac.ulg.montefiore.run.jahmm.Observation;
import be.ac.ulg.montefiore.run.jahmm.Opdf;
import be.ac.ulg.montefiore.run.jahmm.OpdfFactory;

/**
 * @author Dominik Jain
 */
public abstract class AbstractSubHMM<O extends Observation> extends DwellTimeHMM<O> {
	
	protected int numSubLevels;
	protected ArrayList<Opdf<O>> opdfs = null;
	protected OpdfFactory<? extends Opdf<O>> opdfFactory;

	public AbstractSubHMM(int numStates, int numSubLevels, OpdfFactory<? extends Opdf<O>> opdfFactory) {
		super();
		this.numSubLevels = numSubLevels;
		this.opdfFactory = opdfFactory;
		init(numStates);
	}
	
	public AbstractSubHMM(int numSubLevels, OpdfFactory<? extends Opdf<O>> opdfFactory) {
		super();		
		this.numSubLevels = numSubLevels;
		this.opdfFactory = opdfFactory;
	}
	
	@Override
	protected void init(int numStates) {
		super.init(numStates);
		if(numSubLevels == 0) {
			opdfs = new ArrayList<Opdf<O>>();
			for(int i = 0; i < numStates; i++)
				opdfs.add(opdfFactory.factor());
		}
	}
	
	public boolean isBottom() {
		return numSubLevels == 0;
	}

	@Override
	public void learnObservationModel(int state, Collection<? extends Collection<? extends O>> data) throws Exception {
		if(!isBottom())
			throw new Exception("hierarchy unsupported");
		else {
			Vector<O> coll = new Vector<O>();
			for(Collection<? extends O> segment : data)
				coll.addAll(segment);			
			this.opdfs.get(state).fit(coll);
		}
	}
}
