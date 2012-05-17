/*
 * Created on Jun 10, 2010
 */
package probcog.hmm;

import java.util.Collection;
import java.util.Vector;

import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.Observation;
import be.ac.ulg.montefiore.run.jahmm.Opdf;
import be.ac.ulg.montefiore.run.jahmm.OpdfFactory;

/**
 * @author jain
 */
public class HMM<O extends Observation> extends Hmm<O> implements IHMM<O> {
	private static final long serialVersionUID = 1L;
	protected OpdfFactory<? extends Opdf<O>> opdfFactory;
	protected Integer numStates = null;

	public HMM(int nbStates, OpdfFactory<? extends Opdf<O>> opdfFactory) {
		super(nbStates, opdfFactory);
		numStates = nbStates;
	}
	
	/**
	 * constructs an HMM where an appropriate number of states is determined during learning
	 * @param opdfFactory
	 */
	public HMM(OpdfFactory<? extends Opdf<O>> opdfFactory) {
		super();
		this.opdfFactory = opdfFactory;
	}

	public void learnObservationModel(int state, Collection<? extends Collection<? extends O>> data) {
		Vector<O> coll = new Vector<O>();
		for(Collection<? extends O> segment : data)
			coll.addAll(segment);
		System.out.printf("    learning observation model for state %d from %d data points...\n", state, coll.size());
		this.opdfs.get(state).fit(coll);
	}
	
	public void learn(Iterable<? extends SegmentSequence<? extends O>> trainingData, boolean usePseudoCounts) {
		TransitionLearner tl = new TransitionLearner(this.numStates, usePseudoCounts);
		for(SegmentSequence<? extends O> ss : trainingData) {
			O prev = null;
			Integer prevLabel = null;
			for(Segment<? extends O> seg : ss) {
				for(O pt : seg) {
					if(prev != null)
						tl.learn(prevLabel, seg.label);
					prevLabel = seg.label;
					prev = pt;
				}
			}
		}
		setA(tl.finish());
		
		for(int i = 0; i < numStates; i++) {
			Vector<Segment<? extends O>> data = new Vector<Segment<? extends O>>();
			for(SegmentSequence<? extends O> ss : trainingData) {
				Vector<? extends Segment<? extends O>> segs = ss.getSegments(i);
				if(segs == null) 
					continue;
				data.addAll(segs);
			}
			this.learnObservationModel(i, data);
		}
	}

	@Override
	public void setA(double[][] A) {
		this.a = A;		
	}

	@Override
	public void setPi(double[] pi) {
		if(pi.length != numStates)
			throw new IllegalArgumentException("Incorrect array length");
		this.pi = pi;		
	}
	
	@Override
	public Integer getNumStates() {		
		return numStates;
	}
	
	/**
	 * sets the number of states for an HMM whose number of states was previously unknown
	 * @throws IllegalAccessException
	 */
	@Override
	public void setNumStates(int numStates) throws IllegalAccessException {
		if(this.numStates != null)
			throw new IllegalAccessException("Cannot set number of states in model which was constructed with known number of states");
		this.numStates = numStates;
		// initialize the HMM
		init(numStates, opdfFactory);		
	}
	
	@Override
	public IObservationModel<O> getObservationModel(int state) {		
		return new OpdfObservationModel<O>(this.opdfs.get(state));
	}
}
