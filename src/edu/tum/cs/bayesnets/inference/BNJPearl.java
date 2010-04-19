/*
 * Created on Oct 15, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

/**
 * Pearl's polytree algorithm (exact)  
 * @author jain
 */
public class BNJPearl extends BNJInference {

	public BNJPearl(BeliefNetworkEx bn) throws Exception {
		super(bn, edu.ksu.cis.bnj.ver3.inference.exact.Pearl.class);
	}

}
