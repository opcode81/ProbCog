package probcog.srl.directed.inference;

import probcog.srl.directed.bln.AbstractGroundBLN;

public class GibbsSampling extends BNSampler {
	public GibbsSampling(AbstractGroundBLN gbln) throws Exception {
		super(gbln, probcog.bayesnets.inference.GibbsSampling.class);
	}
}
