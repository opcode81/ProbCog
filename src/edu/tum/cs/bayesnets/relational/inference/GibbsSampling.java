package edu.tum.cs.bayesnets.relational.inference;

public class GibbsSampling extends BNSampler {
	public GibbsSampling(GroundBLN gbln) {
		super(gbln, edu.tum.cs.bayesnets.inference.GibbsSampling.class);
	}
}
