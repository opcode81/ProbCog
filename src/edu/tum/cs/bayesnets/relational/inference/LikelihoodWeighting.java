package edu.tum.cs.bayesnets.relational.inference;


public class LikelihoodWeighting extends BNSampler {
	public LikelihoodWeighting(GroundBLN gbln) {
		super(gbln, edu.tum.cs.bayesnets.inference.LikelihoodWeighting.class);
	}
}
