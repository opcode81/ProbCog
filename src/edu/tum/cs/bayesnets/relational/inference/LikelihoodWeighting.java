package edu.tum.cs.bayesnets.relational.inference;

import edu.tum.cs.bayesnets.relational.core.bln.AbstractGroundBLN;


public class LikelihoodWeighting extends BNSampler {
	public LikelihoodWeighting(AbstractGroundBLN gbln) {
		super(gbln, edu.tum.cs.bayesnets.inference.LikelihoodWeighting.class);
	}
}
