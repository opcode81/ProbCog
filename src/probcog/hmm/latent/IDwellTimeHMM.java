/*
 * Created on Jun 2, 2010
 */
package probcog.hmm.latent;

import probcog.hmm.IHMM;

public interface IDwellTimeHMM<O> extends IHMM<O> {
	public double getDwellProbability(int state, int dwellTime);
	public double getTransitionProbability(int from, int dwellTime, int to);
}
