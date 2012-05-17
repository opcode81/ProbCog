package probcog.bayesnets.inference;

import probcog.bayesnets.core.BeliefNetworkEx;

public class SmilePearl extends SmileInference {
	
	public SmilePearl(BeliefNetworkEx bn) throws Exception {
		super(bn, smile.Network.BayesianAlgorithmType.Pearl);
	}

}
