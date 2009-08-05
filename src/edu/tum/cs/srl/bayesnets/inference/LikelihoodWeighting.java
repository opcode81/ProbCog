package edu.tum.cs.srl.bayesnets.inference;

import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;


public class LikelihoodWeighting extends BNSampler {
	public LikelihoodWeighting(AbstractGroundBLN gbln) {
		super(gbln, edu.tum.cs.bayesnets.inference.LikelihoodWeighting.class);
	}
}
