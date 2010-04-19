package edu.tum.cs.bayesnets.inference;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class SmileEPIS extends SmileInference {
	
	public SmileEPIS(BeliefNetworkEx bn) throws Exception {
		super(bn, smile.Network.BayesianAlgorithmType.EpisSampling);
	}

}
