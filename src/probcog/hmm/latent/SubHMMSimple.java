/*
 * Created on Apr 20, 2010
 */
package probcog.hmm.latent;

import java.util.List;

import probcog.analysis.actionrecognition.mocap.BodyPose;
import probcog.hmm.ForwardCalculator;
import probcog.hmm.HMM;
import probcog.hmm.Segment;

import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;
import be.ac.ulg.montefiore.run.jahmm.Opdf;
import be.ac.ulg.montefiore.run.jahmm.OpdfFactory;
import be.ac.ulg.montefiore.run.jahmm.OpdfIndependentGaussiansFactory;
import be.ac.ulg.montefiore.run.jahmm.learn.BaumWelchScaledLearner;
import edu.tum.cs.util.datastruct.ParameterMap;

/**
 * A standard HMM for use as submodel of an LDHMM
 * @author jain
 */
public class SubHMMSimple extends HMM<ObservationVector> implements ISubHMM {

	private static final long serialVersionUID = 1L;
	
	public SubHMMSimple(int nbStates, int numSubLevels, int obsDimension) {
		super(nbStates, getOpdfFactory(obsDimension));
	}

	public SubHMMSimple(int numSubLevels, int obsDimension) {
		super(getOpdfFactory(obsDimension));
	}
	
	protected static OpdfFactory<? extends Opdf<ObservationVector>> getOpdfFactory(int obsDimension) {
		//return new OpdfMultiGaussianFactory(obsDimension);
		return new OpdfIndependentGaussiansFactory(obsDimension);
	}
	
	public void learnViaBaumWelch(List<? extends Segment<BodyPose>> s) {
		BaumWelchScaledLearner bw = new BaumWelchScaledLearner();
		Hmm<ObservationVector> hmm = bw.learn(this, s);
		
		this.pi = hmm.getPi();
		this.a = hmm.getA();
		this.opdfs = hmm.getOpdfs();
	}
	
	public void learnViaClustering(Iterable<? extends Segment<BodyPose>> s, boolean usePseudoCounts) throws Exception {
		SubHMM.learnViaClustering(this, s, usePseudoCounts);
	}

	@Override
	public double getDwellProbability(int state, int dwellTime) {		
		return a[state][state];
	}

	@Override
	public double getTransitionProbability(int from, int dwellTime, int to) {
		return a[from][to];
	}

	@Override
	public void learn(List<? extends Segment<BodyPose>> s, ParameterMap learningParams) throws Exception {
		if(learningParams.getBoolean("learnSubHMMViaBaumWelch"))
			learnViaBaumWelch(s);
		else
			learnViaClustering(s, learningParams.getBoolean("usePseudoCounts"));		
	}
	
	public ForwardCalculator<ObservationVector> getForwardCalculator() {
		return new ForwardCalculator<ObservationVector>(this);		
	}
}
