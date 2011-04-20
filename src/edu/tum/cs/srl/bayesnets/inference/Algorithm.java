/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import java.lang.reflect.Constructor;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.BNJPearl;
import edu.tum.cs.bayesnets.inference.BackwardSampling;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithChildren;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithPriors;
import edu.tum.cs.bayesnets.inference.BeliefPropagation;
import edu.tum.cs.bayesnets.inference.EnumerationAsk;
import edu.tum.cs.bayesnets.inference.IJGP;
import edu.tum.cs.bayesnets.inference.LikelihoodWeighting;
import edu.tum.cs.bayesnets.inference.LikelihoodWeightingWithUncertainEvidence;
import edu.tum.cs.bayesnets.inference.SampleSearch;
import edu.tum.cs.bayesnets.inference.VariableElimination;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;

public enum Algorithm {
	  	// NOTE: Algorithms that may not exist in some distributions are specified using the string constructor
		LikelihoodWeighting("likelihood weighting", null, LikelihoodWeighting.class), 
		LWU("likelihood weighting with uncertain evidence", null, LikelihoodWeightingWithUncertainEvidence.class), 
		GibbsSampling("Gibbs sampling (MCMC)", null, edu.tum.cs.bayesnets.inference.GibbsSampling.class), 
		EPIS("importance sampling based on evidence prepropagation [SMILE]", "edu.tum.cs.bayesnets.inference.SmileEPIS"), 
		BackwardSampling("backward simulation", null, BackwardSampling.class),			 
		BackwardSamplingPriors("backward simulation with prior bias", null, BackwardSamplingWithPriors.class), 
		BackwardSamplingChildren("backward simulation with extended context", null, BackwardSamplingWithChildren.class),
		LiftedBackwardSampling("a lifted version of backw. sampling with ext. context", LiftedBackwardSampling.class, null),
		SmileBackwardSampling("backward simulation [SMILE]", "edu.tum.cs.bayesnets.inference.SmileBackwardSampling"),						 
		SATIS("SAT-IS: satisfiability-based importance sampling", SATIS.class, null), 
		SATISEx("SAT-IS, extended with constraints from CPDs", SATISEx.class, null),
		SATISExGibbs("SAT-IS extended with interspersed Gibbs Sampling steps", SATISExGibbs.class, null),
		SampleSearch("SampleSearch: backtracking search for satisfiable states", null, SampleSearch.class),
		MCSAT("MC-SAT (MCMC method based on SAT-solving)", MCSAT.class, null),
		IJGP("Iterative Join-Graph Propagation", null, IJGP.class),
		BeliefPropagation("Belief Propagation", null, BeliefPropagation.class),
		EnumerationAsk("Enumeration-Ask (exact, highly inefficient)", null, EnumerationAsk.class),
		Pearl("Pearl's algorithm for polytrees (exact)", null, BNJPearl.class),
		SmilePearl("Pearl's algorithm for polytrees (exact) [SMILE]", "edu.tum.cs.bayesnets.inference.SmilePearl"),
		VarElim("variable elimination (exact)", null, VariableElimination.class),
		SampleSearchIB("SampleSearch with intelligent backtracking", null, edu.tum.cs.bayesnets.inference.SampleSearchIB.class),
		Experimental2("an experimental algorithm (usually beta)", "dev.SampleSearchIBLearning"),
		Experimental2b("an experimental algorithm (usually beta)", "dev.SampleSearchIBLearning2"),
		BackwardSampleSearch("backward sample searching","dev.BackwardSampleSearch"),
		Experimental3("an experimental algorithm (usually beta)", "dev.SampleSearch2"),
		ACE("ACE 2.0 (arithmetic circuits evaluation; requires installation)", null, edu.tum.cs.bayesnets.inference.ACE.class),
		SampleSearchChoco("SampleSearch that exploits the Choco constraint solver for search.", "dev.SampleSearchChoco"),
		QGraphInference("Inference based on counting in the training database", "dev.QGraphInference");
		
		protected String description;
		protected Class<? extends edu.tum.cs.bayesnets.inference.Sampler> bnClass;
		protected Class<? extends Sampler> blnClass;
		
		private Algorithm(String description, Class<? extends Sampler> blnClass, Class<? extends edu.tum.cs.bayesnets.inference.Sampler> bnClass) {
			this.description = description;
			this.blnClass = blnClass;
			this.bnClass = bnClass;
		}
		
		/**
		 * this constructor can be used for classes that are not necessarily part of distributions of the project (won't get compilation problems)
		 * @param description
		 * @param className name of either a class derived from Sampler or edu.tum.cs.bayesnets.inference.Sampler
		 */
		@SuppressWarnings("unchecked")
		private Algorithm(String description, String className) {
			this.description = description;
			try {
				Class<?> cl = Class.forName(className);
				try {
					cl.getConstructor(BeliefNetworkEx.class);
					bnClass = (Class<? extends edu.tum.cs.bayesnets.inference.Sampler>)cl;
				}
				catch(Exception e) {
					blnClass = (Class<? extends Sampler>)cl;
				}
			} 
			catch(ClassNotFoundException e) {	
			} 
			catch(NoClassDefFoundError e) {				
			}
		}
		
		public String getDescription() {
			return description;
		}
		
		public Sampler createSampler(AbstractGroundBLN gbln) throws Exception {
			Sampler sampler = null;
			if(bnClass != null) {
				sampler = new BNSampler(gbln, bnClass);
			}
			else if(blnClass != null) {	
				Constructor<? extends Sampler> constructor;
				try {
					 constructor = blnClass.getConstructor(gbln.getClass());
				}
				catch(NoSuchMethodException e) {
					throw new Exception("Don't know how to instantiate a sampler for the algorithm '" + toString() + "'");
				}
				sampler = constructor.newInstance(gbln);
			}
			else
				throw new Exception("Cannot instantiate a sampler for the algorithm '" + toString() + "' - sampler class not found. The algorithm may not be available in your distribution.");
			return sampler;
		}
		
		public static void printList(String indentation) {
			for(Algorithm a : Algorithm.values()) 
				System.out.printf("%s%-28s  %s\n", indentation, a.toString(), a.getDescription());
		}
}