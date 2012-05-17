package probcog.hmm;

import java.util.List;


import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.Observation;


/**
 * the forward algorithm for basic/standard HMMs
 */
public class ForwardCalculator<O extends Observation> implements IObservationModel<O>
{	
	protected double[] bel;	
	protected Hmm<O> hmm;
	protected int step = 0;
	
	public ForwardCalculator(Hmm<O> hmm) {
		this.hmm = hmm;
		bel = new double[hmm.nbStates()];
	}
	
	public double step(O o) {
		double obsProb = 0.0;
		if(step == 0) {		
			for(int i = 0; i < hmm.nbStates(); i++) {
				bel[i] = hmm.getPi(i) * hmm.getOpdf(i).probability(o);
				obsProb += bel[i];
			}
		}
		else {
			double[] bel2 = new double[hmm.nbStates()];
			for(int j = 0; j < hmm.nbStates(); j++) {
				for(int i = 0; i < hmm.nbStates(); i++) {				
					double pTrans = hmm.getAij(i, j);
					bel2[j] += bel[i] * pTrans;					
				}
				bel2[j] *= hmm.getOpdf(j).probability(o);
				obsProb += bel2[j];
			}
			bel = bel2;
		}
		
		// normalize beliefs
		for(int i = 0; i < hmm.nbStates(); i++)
			bel[i] /= obsProb;
		
		++step;
		
		return obsProb;
	}
		
	public void run(List<? extends O> observations) {
		for(O o : observations)
			step(o);
	}

	@Override
	public double getObservationProbability(O observation) {
		return step(observation);
	}
	
}
