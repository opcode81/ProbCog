/*
 * Created on Jun 4, 2010
 */
package probcog.hmm.latent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

import be.ac.ulg.montefiore.run.jahmm.Observation;
import be.ac.ulg.montefiore.run.jahmm.Opdf;
import be.ac.ulg.montefiore.run.jahmm.OpdfFactory;

/**
 * @author jain
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
