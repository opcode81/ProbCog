package probcog.bayesnets.inference;

import probcog.bayesnets.core.BeliefNetworkEx;

public class SmileEPIS extends SmileInference {
	
	public SmileEPIS(BeliefNetworkEx bn) throws Exception {
		super(bn, smile.Network.BayesianAlgorithmType.EpisSampling);
	}

}
