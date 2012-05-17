/*
 * Created on Jun 10, 2010
 */
package probcog.hmm;

import java.util.Collection;

/**
 * interface for essential HMM functionality
 * @author jain
*/
public interface IHMM<O> {
	public IObservationModel<O> getObservationModel(int state);
	public double getPi(int state);
	public Integer getNumStates();
	public void setNumStates(int numStates) throws IllegalAccessException;
	public void setA(double[][] A);
	public void setPi(double[] pi);
	public void learnObservationModel(int state, Collection<? extends Collection<? extends O>> data) throws Exception;
}
