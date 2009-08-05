package edu.tum.cs.srl.bayesnets.inference;

import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;

public class GibbsSampling extends BNSampler {
	public GibbsSampling(AbstractGroundBLN gbln) {
		super(gbln, edu.tum.cs.bayesnets.inference.GibbsSampling.class);
	}
}
