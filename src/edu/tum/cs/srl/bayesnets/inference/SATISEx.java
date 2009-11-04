package edu.tum.cs.srl.bayesnets.inference;

import edu.tum.cs.bayesnets.inference.SATIS_BSampler;
import edu.tum.cs.logic.sat.ClausalKB;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;

/**
 * extended version of the SAT-IS algorithm, where the knowledge base is augmented with formulas 
 * based on 0 entries in probabilistic constraints, which factually represent deterministic 
 * constraints 
 * @author jain
 */
public class SATISEx extends SATIS {

	public SATISEx(GroundBLN bln) throws Exception {
		super(bln);
	}
	
	@Override
	protected ClausalKB getClausalKB() throws Exception {
		ClausalKB ckb = super.getClausalKB();
		
		// extend the KB with formulas based on a CPD analysis
		SATIS_BSampler.extendKBWithDeterministicConstraintsInCPTs(gbln.getGroundNetwork(), gbln.getCoupling(), ckb, gbln.getDatabase());
		
		return ckb;
	}
}
