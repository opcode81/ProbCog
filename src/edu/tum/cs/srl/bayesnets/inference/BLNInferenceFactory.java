/*
 * Created on Oct 22, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import edu.tum.cs.bayesnets.inference.BNJPearl;
import edu.tum.cs.bayesnets.inference.BNJVariableElimination;
import edu.tum.cs.bayesnets.inference.BackwardSampling;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithChildren;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithPriors;
import edu.tum.cs.bayesnets.inference.EnumerationAsk;
import edu.tum.cs.bayesnets.inference.LikelihoodWeightingWithUncertainEvidence;
import edu.tum.cs.bayesnets.inference.SampleSearch;
import edu.tum.cs.bayesnets.inference.SmileBackwardSampling;
import edu.tum.cs.bayesnets.inference.SmileEPIS;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;

public class BLNInferenceFactory {
	
	public static enum Algorithm {
			LikelihoodWeighting("likelihood weighting"), 
			LWU("likelihood weighting with uncertain evidence"), 
			GibbsSampling("Gibbs sampling (MCMC)"), 
			EPIS("importance sampling based on evidence prepropagation (SMILE)"), 
			BackwardSampling("backward simulation"),			 
			BackwardSamplingPriors("backward simulation with prior bias"), 
			BackwardSamplingWithChildren("backward simulation with extended context"),
			LiftedBackwardSampling("a lifted version of backw. sampling with ext. context"),
			SmileBackwardSampling("backward simulation (SMILE)"),						 
			SATIS("SAT-IS: satisfiability-based importance sampling"), 
			SATISEx("SAT-IS, extended with constraints from CPDs"),
			SATISExGibbs("SAT-IS extended with interspersed Gibbs Sampling steps"),			 
			MCSAT("MC-SAT (MCMC method based on SAT-solving)"),			 
			EnumerationAsk("Enumeration-Ask (exact, highly inefficient)"),
			Pearl("Pearl's algorithm for polytrees (exact)"), 
			VarElim("variable elimination (exact)"),
			Experimental("an experimental algorithm (usually beta)");
			
			protected String description;
			
			private Algorithm(String description) {
				this.description = description;
			}
			
			public String getDescription() {
				return description;
			}
	};
	
	public static Sampler createSampler(Algorithm algorithm, AbstractGroundBLN gbln) throws Exception {
		Sampler sampler;
		switch(algorithm) {
		case LikelihoodWeighting: 
			sampler = new LikelihoodWeighting(gbln); break;
		case LWU: 
			sampler = new BNSampler(gbln, LikelihoodWeightingWithUncertainEvidence.class); break;
		case GibbsSampling:	
			sampler = new GibbsSampling(gbln); break;
		case EPIS:
			sampler = new BNSampler(gbln, SmileEPIS.class); break;
		case SmileBackwardSampling:
			sampler = new BNSampler(gbln, SmileBackwardSampling.class); break;
		case BackwardSampling:
			sampler = new BNSampler(gbln, BackwardSampling.class); break;
		case BackwardSamplingPriors:
			sampler = new BNSampler(gbln, BackwardSamplingWithPriors.class); break;
		case BackwardSamplingWithChildren:
			sampler = new BNSampler(gbln, BackwardSamplingWithChildren.class); break;
		case LiftedBackwardSampling:
			sampler = new LiftedBackwardSampling(gbln); break;
		case SATIS:
			sampler = new SATIS((GroundBLN)gbln); break;
		case SATISEx:
			sampler = new SATISEx((GroundBLN)gbln); break;
		case SATISExGibbs:
			sampler = new SATISExGibbs((GroundBLN)gbln); break;
		case MCSAT:
			sampler = new MCSAT((GroundBLN)gbln); break;
		case EnumerationAsk:
			sampler = new BNSampler(gbln, EnumerationAsk.class); break;
		case Pearl:
			sampler = new BNSampler(gbln, BNJPearl.class); break;
		case VarElim:
			sampler = new BNSampler(gbln, BNJVariableElimination.class); break;
		case Experimental:
			sampler = new BNSampler(gbln, SampleSearch.class); break;
		default: 
			throw new Exception("Don't know how to instantiate a sampler for the algorithm '" + algorithm.toString() + "'");
		}
		return sampler;
	}
}
