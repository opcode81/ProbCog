package probcog.bayesnets.inference;

import probcog.bayesnets.core.BeliefNetworkEx;

public class SmileBackwardSampling extends SmileInference {

	public SmileBackwardSampling(BeliefNetworkEx bn) throws Exception {
		super(bn, smile.Network.BayesianAlgorithmType.BackSampling);
	}

}
