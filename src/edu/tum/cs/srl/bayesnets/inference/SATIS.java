package edu.tum.cs.srl.bayesnets.inference;

import java.util.HashSet;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.inference.SATIS_BSampler;
import edu.tum.cs.bayesnets.inference.Sampler;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.sat.ClausalKB;
import edu.tum.cs.logic.sat.Clause;
import edu.tum.cs.logic.sat.SampleSAT;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;

/**
 * SAT-IS: satisfiability-based importance sampling for inference in mixed networks with probabilistic and deterministic constraints
 * 
 * @author jain
 */
public class SATIS extends BNSampler {

	GroundBLN gbln;
	/**
	 * the SAT sampler that is used to sample the sub-state that is determined by the hard logical constraints 
	 */
	SampleSAT ss;
	/**
	 * the set of nodes whose values are determined by the SAT sampler (because they are part of a hard logical constraint)
	 */
	HashSet<BeliefNode> determinedVars;
	
	public SATIS(GroundBLN bln) throws Exception {
		super(bln, SATIS_BSampler.class);
		gbln = bln;		
		initSATSampler();
	}
	
	protected void initSATSampler() throws Exception {
		System.out.println("initializing SAT sampler...");
		// create SAT sampler
		PossibleWorld state = new PossibleWorld(gbln.getWorldVars());
		ClausalKB ckb = getClausalKB();
		ss = new SampleSAT(ckb, state, gbln.getWorldVars(), gbln.getDatabase().getEntries());
		
		// get the set of variables that is determined by the sat sampler
		determinedVars = new HashSet<BeliefNode>();
		for(Clause c : ckb) {
			for(GroundLiteral lit : c.lits) {
				BeliefNode var = gbln.getVariable(lit.gndAtom);
				if(var == null)
					throw new Exception("Could not find node corresponding to ground atom '" + lit.gndAtom.toString() + "' with index " + lit.gndAtom.index + "; set of mapped ground atoms is " + gbln.getCoupling().getCoupledGroundAtoms());
				determinedVars.add(var);
			}
		}		
	}
	
	protected ClausalKB getClausalKB() throws Exception {
		return new ClausalKB(gbln.getKB());
	}
	
	@Override
	protected Sampler getSampler() {
		return new SATIS_BSampler(gbln.getGroundNetwork(), ss, gbln.getCoupling(), determinedVars);
	}
}
