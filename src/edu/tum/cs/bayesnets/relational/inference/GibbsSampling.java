package edu.tum.cs.bayesnets.relational.inference;

import edu.tum.cs.bayesnets.relational.core.bln.AbstractGroundBLN;

public class GibbsSampling extends BNSampler {
	public GibbsSampling(AbstractGroundBLN gbln) {
		super(gbln, edu.tum.cs.bayesnets.inference.GibbsSampling.class);
	}
}
