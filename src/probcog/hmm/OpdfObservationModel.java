/*
 * Created on Jun 7, 2010
 */
package probcog.hmm;

import be.ac.ulg.montefiore.run.jahmm.Observation;
import be.ac.ulg.montefiore.run.jahmm.Opdf;

public class OpdfObservationModel<O extends Observation> implements IObservationModel<O> {

	Opdf<O> model;
	
	public OpdfObservationModel(Opdf<O> model) {
		this.model = model;
	}
	
	@Override
	public double getObservationProbability(O observation) {
		return model.probability(observation);
	}	
}
