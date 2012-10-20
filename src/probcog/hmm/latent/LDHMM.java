/*
 * Created on May 20, 2010
 */
package probcog.hmm.latent;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import probcog.hmm.IObservationModel;
import probcog.hmm.Segment;
import probcog.hmm.SegmentSequence;
import probcog.hmm.TransitionLearner;

import be.ac.ulg.montefiore.run.jahmm.ObservationReal;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;
import edu.tum.cs.util.datastruct.ParameterMap;

/**
 * latend dynamic hidden semi-Markov modell, where the observation model is given by
 * a sub-HMM
 * @author jain
 */
public class LDHMM extends DwellTimeHMM<ObservationVector> implements Serializable{
	private static final long serialVersionUID = 1L;
	public ISubHMM[] subHMMs;
	protected int numSubStates, obsDimension;

	/**
	 * @param numStates
	 * @param numSubStates if 0, automatically determine using EM clustering
	 * @param obsDimension
	 */
	public LDHMM(int numStates, int numSubStates, int obsDimension) {
		super(numStates);
		subHMMs = new ISubHMM[numStates];
		this.obsDimension = obsDimension;		
		this.numSubStates = numSubStates;
	}
	
	public void learn(Iterable<? extends SegmentSequence<? extends ObservationVector>> seqs, Class<? extends ISubHMM> subHMMClass, ParameterMap learningParams) throws Exception {
		// for each segment type, learn sub-hmm and dwell time distributions
		System.out.println("learning...");
		boolean usePseudoCounts = learningParams.getBoolean("usePseudoCounts");
		for(int i = 0; i < numStates; i++) {			
			//i = 4;

			// collect relevant training segments
			List<Segment<? extends ObservationVector>> trainingSegs = new Vector<Segment<? extends ObservationVector>>();
			for(SegmentSequence<? extends ObservationVector> seq : seqs) {
				Vector<? extends Segment<? extends ObservationVector>> segs = seq.getSegments(i);
				if(segs == null)
					continue;
				trainingSegs.addAll(segs);
			}
			if(trainingSegs.size() == 0) {
				for(SegmentSequence<? extends ObservationVector> seq : seqs)
					System.out.println(seq);
				throw new Exception("No training data available for label " + i);
			}
			
			// learn sub-HMM
			System.out.printf("  state %d (%d segments as training data)\n", i, trainingSegs.size());
			int numSubLevels = 0;
			ISubHMM hmm;
			if(numSubStates > 0)
				hmm = subHMMClass.getConstructor(int.class, int.class, int.class).newInstance(numSubStates, numSubLevels, obsDimension);
			else
				hmm = subHMMClass.getConstructor(int.class, int.class).newInstance(numSubLevels, obsDimension);
			hmm.learn(trainingSegs, learningParams);
			subHMMs[i] = hmm;
			System.out.printf("    %d states in sub-HMM\n", hmm.getNumStates());
			
			// learn dwell time distribution			
			Vector<ObservationReal> lengths = new Vector<ObservationReal>();
			for(Segment<? extends ObservationVector> seg : trainingSegs)
				lengths.add(new ObservationReal(seg.size()));
			learnDwellTimeDistribution(i, lengths);
			System.out.printf("    dwell time: %s\n", dwellTimeDist[i].toString());
		}
		
		// learn transition matrix for segments
		learnTransitionMatrix(seqs, usePseudoCounts);
		
		// initial distribution: assume uniform
		Arrays.fill(pi, 1.0 / numStates);
	}	
	
	public void learnTransitionMatrix(Iterable<? extends SegmentSequence<?>> seqs, boolean usePseudoCounts) {
		TransitionLearner tl = new TransitionLearner(numStates, usePseudoCounts);
		for(SegmentSequence<?> seq : seqs) {
			Segment<?> prev = null;
			for(Segment<?> seg : seq) {
				if(prev != null)
					tl.learn(prev.label, seg.label);
				prev = seg;				
			}
		}
		this.A = tl.finish();		
	}
	
	public void write(String filename) throws FileNotFoundException, IOException {
		// save to file
		java.io.ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
		oos.writeObject(this);		
		oos.close();
	}
	
	public static LDHMM fromFile(String filename) throws FileNotFoundException, IOException, ClassNotFoundException {
		// load from file
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
		LDHMM hhmm = (LDHMM) ois.readObject();
		ois.close();
		return hhmm;
	}

	@Override
	public IObservationModel<ObservationVector> getObservationModel(int state) {		
		//return new GenericForwardCalculator(this.subHMMs[state]);
		return this.subHMMs[state].getForwardCalculator();
	}

	@Override
	public void learnObservationModel(int state, Collection<? extends Collection<? extends ObservationVector>> data) {
		throw new RuntimeException("not supported");		
	}
}
