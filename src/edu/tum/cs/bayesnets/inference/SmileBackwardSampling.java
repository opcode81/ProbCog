package edu.tum.cs.bayesnets.inference;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class SmileBackwardSampling extends SmileInference {

	public SmileBackwardSampling(BeliefNetworkEx bn) throws Exception {
		super(bn, smile.Network.BayesianAlgorithmType.BackSampling);
	}

}
