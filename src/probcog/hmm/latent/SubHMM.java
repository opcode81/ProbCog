/*
 * Created on Jun 4, 2010
 */
package probcog.hmm.latent;

import java.util.List;
import java.util.Vector;

import probcog.clustering.multidim.EMClusterer;
import probcog.clustering.multidim.KMeansClusterer;
import probcog.clustering.multidim.MultiDimClusterer;
import probcog.hmm.DistributionLearner;
import probcog.hmm.IObservationModel;
import probcog.hmm.OpdfObservationModel;
import probcog.hmm.Segment;
import probcog.hmm.SegmentSequence;
import probcog.hmm.TransitionLearner;

import weka.clusterers.EM;
import weka.clusterers.SimpleKMeans;
import be.ac.ulg.montefiore.run.jahmm.ObservationReal;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;
import be.ac.ulg.montefiore.run.jahmm.OpdfMultiGaussianFactory;
import edu.tum.cs.util.datastruct.ParameterMap;

/**
 * A SubHMM (for use as a submodel of LDHMM), which is itself a dwell-time HMM
 * @author jain
 */
public class SubHMM extends AbstractSubHMM<ObservationVector> implements ISubHMM {

	protected int obsDimension;
	
	public SubHMM(int numStates, int numSubLevels, int obsDimension) {
		super(numStates, numSubLevels, new OpdfMultiGaussianFactory(obsDimension));
		this.obsDimension = obsDimension;
	}
	
	public SubHMM(int numSubLevels, int obsDimension) {
		super(numSubLevels, new OpdfMultiGaussianFactory(obsDimension));
		this.obsDimension = obsDimension;
	}

	@Override
	public void learn(List<? extends Segment<? extends ObservationVector>> s, ParameterMap learningParams) throws Exception {
		if(learningParams.getBoolean("learnSubHMMViaBaumWelch"))
			throw new Exception("Baum-Welch learning not supported by class " + this.getClass().getName());
		// learn pi, A and observation models
		SegmentSequence<? extends ObservationVector> ss = learnViaClustering(this, s, learningParams.getBoolean("usePseudoCounts"));
		
		// learn dwell time distributions
		for(int i = 0; i < this.numStates; i++) {
			Vector<ObservationReal> lengths = new Vector<ObservationReal>();
			for(Segment<? extends ObservationVector> seg : ss.getSegments(i))
				lengths.add(new ObservationReal(seg.size()));
			this.learnDwellTimeDistribution(i, lengths);
		}
	}
	
	public static SegmentSequence<? extends ObservationVector> learnViaClustering(IDwellTimeHMM<ObservationVector> hmm, Iterable<? extends Segment<? extends ObservationVector>> s, boolean usePseudoCounts) throws Exception {
		final int dim = s.iterator().next().firstElement().dimension();
		Integer numStates = hmm.getNumStates();
		
		// clustering
		MultiDimClusterer<?> clusterer;
		if(numStates != null)
			clusterer = new KMeansClusterer(new SimpleKMeans(), dim, numStates);
		else
			clusterer = new EMClusterer(new EM(), dim);
		for(Segment<? extends ObservationVector> seg : s)
			for(ObservationVector p : seg)
				//clusterer.addInstance(p.getArray());
				clusterer.addInstance(p.values()); // TODO slow, performs clone
		clusterer.buildClusterer();
		if(numStates == null) {
			numStates = clusterer.getWekaClusterer().numberOfClusters();
			hmm.setNumStates(numStates);
		}		
		
		// count transitions and partition
		// partition observations according to clustering
		TransitionLearner tl = new TransitionLearner(numStates, usePseudoCounts);
		DistributionLearner dl = new DistributionLearner(numStates, usePseudoCounts);
		SegmentSequence<ObservationVector> segseq = new SegmentSequence<ObservationVector>("foo");
		for(Segment<? extends ObservationVector> seg : s) {			
			int prev = -1;
			for(ObservationVector p : seg) {
				//int c = clusterer.classify(p.getArray());
				int c = clusterer.classify(p.values()); // TODO inefficient, clones values
				segseq.build(c, p);				
				if(prev == -1)
					dl.learn(c);
				else
					tl.learn(prev, c);
				prev = c;
			}
			segseq.buildEndSegment();
		}
		hmm.setA(tl.finish());
		hmm.setPi(dl.finish()); 
		
		// learn observation models
		for(int i = 0; i < numStates; i++) {
			hmm.learnObservationModel(i, segseq.getSegments(i));
			//System.out.printf("    sub-hmm %d: %d data points\n", i, partitions.get(i).size());
		}	
		
		return segseq;
	}

	@Override
	public IObservationModel<ObservationVector> getObservationModel(int state) {
		return new OpdfObservationModel<ObservationVector>(this.opdfs.get(state));
	}

	@Override
	public IObservationModel<ObservationVector> getForwardCalculator() {
		return new DwellTimeForwardCalculator<ObservationVector>(this);
	}
}
