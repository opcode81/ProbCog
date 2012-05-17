/*
 * Created on Jun 4, 2010
 */
package probcog.hmm.latent;

import java.util.List;

import probcog.analysis.actionrecognition.mocap.BodyPose;
import probcog.hmm.IObservationModel;
import probcog.hmm.Segment;

import edu.tum.cs.util.datastruct.ParameterMap;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;

/**
 * interface a sub-HMM of a hierarchical HMM must implement
 * @author jain
 */
public interface ISubHMM extends IDwellTimeHMM<ObservationVector> {
	public void learn(List<? extends Segment<BodyPose>> s, ParameterMap learningParams) throws Exception;
	public IObservationModel<ObservationVector> getForwardCalculator();
}
