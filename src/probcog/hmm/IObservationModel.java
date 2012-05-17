/*
 * Created on Jun 2, 2010
 */
package probcog.hmm;

/**
 * @author jain
*/
public interface IObservationModel<O> {
	public double getObservationProbability(O observation);
}
