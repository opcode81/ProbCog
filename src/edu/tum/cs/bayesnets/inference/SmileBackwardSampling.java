package edu.tum.cs.bayesnets.inference;

import java.io.FileNotFoundException;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class SmileBackwardSampling extends SmileInference {

	public SmileBackwardSampling(BeliefNetworkEx bn) throws FileNotFoundException {
		super(bn, smile.Network.BayesianAlgorithmType.BackSampling);
	}

}
