package edu.tum.cs.srl.bayesnets.inference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SATIS_BSampler;
import edu.tum.cs.bayesnets.inference.Sampler;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.ClausalKB;
import edu.tum.cs.logic.sat.Clause;
import edu.tum.cs.logic.sat.SampleSAT;
import edu.tum.cs.srl.AbstractVariable;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.srl.bayesnets.bln.coupling.VariableLogicCoupling;

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
	boolean unitPropagation = false;
	
	public SATIS(GroundBLN bln) throws Exception {
		super(bln, SATIS_BSampler.class);
		gbln = bln;
		this.paramHandler.add("unitPropagation", "setUnitPropagation");

		// create SAT sampler
		PossibleWorld state = new PossibleWorld(gbln.getWorldVars());
		ss = new SampleSAT(state, gbln.getWorldVars(), gbln.getDatabase().getEntries());
		//ss = new SampleSATPriors(state, gbln.getWorldVars(), gbln.getDatabase().getEntries(), gbln.getGroundNetwork());
		paramHandler.addSubhandler(ss.getParameterHandler());
	}
	
	public void setUnitPropagation(boolean enabled) {
		unitPropagation = enabled;
	}
	
	protected void initSATSampler() throws Exception {
		System.out.println("initializing SAT sampler...");
				
		if(unitPropagation) ss.enableUnitPropagation();
		this.ss.setDebugMode(debug);
		ClausalKB ckb = getClausalKB();
		ss.initConstraints(ckb);		
		
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
	protected Sampler getSampler() throws Exception {
		initSATSampler();		
		return new SATIS_BSampler(gbln.getGroundNetwork(), ss, gbln.getCoupling(), determinedVars);
	}
	
	/**
	 * SampleSAT samples uniformly from the set of solutions.
	 * If there is a large number of solutions and many of them are improbable, then
	 * the sampled distribution may be dominated by a few high-probability worlds that
	 * were sampled. To prevent this, we use the prior to initialize random variables,
	 * thus introducing a slight bias towards worlds with higher probability.
	 * @author jain
	 */
	protected class SampleSATPriors extends SampleSAT {

		BeliefNetworkEx bn;
		HashMap<BeliefNode, double[]> priors = null;
		Random generator;
		
		public SampleSATPriors(PossibleWorld state, WorldVariables vars, Iterable<? extends AbstractVariable<?>> db, BeliefNetworkEx bn) throws Exception {
			super(state, vars, db);
			this.bn = bn;
			generator = new Random();
		}
		
		protected void setRandomState() {
			if(priors == null)
				priors = bn.computePriors(evidenceDomainIndices);
			VariableLogicCoupling coupling = gbln.getCoupling();
			for(BeliefNode node : bn.bn.getNodes()) {
				if(!coupling.hasCoupling(node))
					continue;
				int domIdx = Sampler.sample(priors.get(node), generator);
				coupling.setVariableValue(node, domIdx, this.state);
			}
		}
	}
}
