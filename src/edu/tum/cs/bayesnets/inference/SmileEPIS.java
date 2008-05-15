package edu.tum.cs.bayesnets.inference;

import java.io.FileNotFoundException;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class SmileEPIS extends SmileInference {
	
	public SmileEPIS(BeliefNetworkEx bn) throws FileNotFoundException {
		super(bn, smile.Network.BayesianAlgorithmType.EpisSampling);
	}

}
