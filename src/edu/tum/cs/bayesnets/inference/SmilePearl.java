package edu.tum.cs.bayesnets.inference;

import java.io.FileNotFoundException;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class SmilePearl extends SmileInference {
	
	public SmilePearl(BeliefNetworkEx bn) throws FileNotFoundException {
		super(bn, smile.Network.BayesianAlgorithmType.Pearl);
	}

}
