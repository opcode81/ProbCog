package edu.tum.cs.bayesnets.inference;

import java.io.FileNotFoundException;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class BackwardSampling extends SmileInference {

	public BackwardSampling(BeliefNetworkEx bn) throws FileNotFoundException {
		super(bn, smile.Network.BayesianAlgorithmType.BackSampling);
	}

}
