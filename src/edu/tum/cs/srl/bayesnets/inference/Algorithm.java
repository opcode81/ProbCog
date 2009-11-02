/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import java.lang.reflect.Constructor;

import edu.tum.cs.bayesnets.inference.BNJPearl;
import edu.tum.cs.bayesnets.inference.BNJVariableElimination;
import edu.tum.cs.bayesnets.inference.BackwardSampling;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithChildren;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithPriors;
import edu.tum.cs.bayesnets.inference.EnumerationAsk;
import edu.tum.cs.bayesnets.inference.IJGP;
import edu.tum.cs.bayesnets.inference.LikelihoodWeighting;
import edu.tum.cs.bayesnets.inference.LikelihoodWeightingWithUncertainEvidence;
import edu.tum.cs.bayesnets.inference.SampleSearch;
import edu.tum.cs.bayesnets.inference.SmileBackwardSampling;
import edu.tum.cs.bayesnets.inference.SmileEPIS;
import edu.tum.cs.bayesnets.inference.SmilePearl;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;

public enum Algorithm {
		LikelihoodWeighting("likelihood weighting", null, LikelihoodWeighting.class), 
		LWU("likelihood weighting with uncertain evidence", null, LikelihoodWeightingWithUncertainEvidence.class), 
		GibbsSampling("Gibbs sampling (MCMC)", GibbsSampling.class, null), 
		EPIS("importance sampling based on evidence prepropagation [SMILE]", null, SmileEPIS.class), 
		BackwardSampling("backward simulation", null, BackwardSampling.class),			 
		BackwardSamplingPriors("backward simulation with prior bias", null, BackwardSamplingWithPriors.class), 
		BackwardSamplingWithChildren("backward simulation with extended context", null, BackwardSamplingWithChildren.class),
		LiftedBackwardSampling("a lifted version of backw. sampling with ext. context", LiftedBackwardSampling.class, null),
		SmileBackwardSampling("backward simulation [SMILE]", null, SmileBackwardSampling.class),						 
		SATIS("SAT-IS: satisfiability-based importance sampling", SATIS.class, null), 
		SATISEx("SAT-IS, extended with constraints from CPDs", SATISEx.class, null),
		SATISExGibbs("SAT-IS extended with interspersed Gibbs Sampling steps", SATISExGibbs.class, null),			 
		MCSAT("MC-SAT (MCMC method based on SAT-solving)", MCSAT.class, null),
		IJGP("Iterative Join-Graph Propagation", null, IJGP.class),
		EnumerationAsk("Enumeration-Ask (exact, highly inefficient)", null, EnumerationAsk.class),
		Pearl("Pearl's algorithm for polytrees (exact)", null, BNJPearl.class),
		SmilePearl("Pearl's algorithm for polytrees (exact) [SMILE]", null, SmilePearl.class),
		VarElim("variable elimination (exact)", null, BNJVariableElimination.class),
		Experimental("an experimental algorithm (usually beta)", null, SampleSearch.class);
		
		protected String description;
		protected Class<? extends edu.tum.cs.bayesnets.inference.Sampler> bnClass;
		protected Class<? extends Sampler> blnClass;
		
		private Algorithm(String description, Class<? extends Sampler> blnClass, Class<? extends edu.tum.cs.bayesnets.inference.Sampler> bnClass) {
			this.description = description;
			this.blnClass = blnClass;
			this.bnClass = bnClass;
		}
		
		public String getDescription() {
			return description;
		}
		
		public Sampler createSampler(AbstractGroundBLN gbln) throws Exception {
			Sampler sampler;
			if(bnClass != null) {
				sampler = new BNSampler(gbln, bnClass);
			}
			else {	
				Constructor<? extends Sampler> constructor;
				try {
					 constructor = blnClass.getConstructor(gbln.getClass());
				}
				catch(NoSuchMethodException e) {
					throw new Exception("Don't know how to instantiate a sampler for the algorithm '" + toString() + "'");
				}
				sampler = constructor.newInstance(gbln);
			}
			return sampler;
		}
}