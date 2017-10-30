/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.directed.inference;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.inference.BNJPearl;
import probcog.bayesnets.inference.BackwardSampling;
import probcog.bayesnets.inference.BackwardSamplingWithChildren;
import probcog.bayesnets.inference.BackwardSamplingWithPriors;
import probcog.bayesnets.inference.BeliefPropagation;
import probcog.bayesnets.inference.EnumerationAsk;
import probcog.bayesnets.inference.IJGP;
import probcog.bayesnets.inference.LikelihoodWeighting;
import probcog.bayesnets.inference.SampleSearch;
import probcog.bayesnets.inference.VariableElimination;
import probcog.exception.ProbCogException;
import probcog.srl.directed.bln.AbstractGroundBLN;

/**
 * Enumeration of inference methods.
 * @author Dominik Jain
 */
public enum Algorithm {
	  	// NOTE: Algorithms that may not exist in some distributions are specified using the string constructor
		LikelihoodWeighting("likelihood weighting", null, LikelihoodWeighting.class), 
		GibbsSampling("Gibbs sampling (MCMC)", null, probcog.bayesnets.inference.GibbsSampling.class), 
		EPIS("importance sampling based on evidence prepropagation [SMILE]", "probcog.inference.SmileEPIS"), 
		BackwardSampling("backward simulation", null, BackwardSampling.class),			 
		BackwardSamplingPriors("backward simulation with prior bias", null, BackwardSamplingWithPriors.class), 
		BackwardSamplingChildren("backward simulation with extended context", null, BackwardSamplingWithChildren.class),
		LiftedBackwardSampling("a lifted version of backw. sampling with ext. context", LiftedBackwardSampling.class, null),
		SmileBackwardSampling("backward simulation [SMILE]", "probcog.bayesnets.inference.SmileBackwardSampling"),						 
		SATIS("SAT-IS: satisfiability-based importance sampling", SATIS.class, null), 
		SATISEx("SAT-IS, extended with constraints from CPDs", SATISEx.class, null),
		SATISExGibbs("SAT-IS extended with interspersed Gibbs Sampling steps", SATISExGibbs.class, null),
		SampleSearch("SampleSearch: backtracking search for satisfiable states", null, SampleSearch.class),
		SampleSearchOld("SampleSearch: backtracking search for satisfiable states", "dev.SampleSearchOld"),
		MCSAT("MC-SAT (MCMC method based on SAT-solving)", MCSAT.class, null),
		IJGP("Iterative Join-Graph Propagation", null, IJGP.class),
		BeliefPropagation("Belief Propagation", null, BeliefPropagation.class),
		EnumerationAsk("Enumeration-Ask (exact)", null, EnumerationAsk.class),
		Pearl("Pearl's algorithm for polytrees (exact)", null, BNJPearl.class),
		SmilePearl("Pearl's algorithm for polytrees (exact) [SMILE]", "probcog.bayesnets.inference.SmilePearl"),
		VarElim("variable elimination (exact)", null, VariableElimination.class),		
		SampleSearchBJ("SampleSearch with backjumping", null, probcog.bayesnets.inference.SampleSearchBJ.class),
		SampleSearchBJLearning("SampleSearch with backjumping and constraint learning", null, probcog.bayesnets.inference.SampleSearchBJLearning.class),
		Experimental2("an experimental algorithm (usually beta)", "dev.SampleSearchIBLearning"),
		Experimental2b("an experimental algorithm (usually beta)", "dev.SampleSearchIBLearning2"),
		Experimental2c("an experimental algorithm (usually beta)", "dev.SampleSearchBJLearning"),
		BackwardSampleSearch("backward SampleSearch", null, probcog.bayesnets.inference.BackwardSampleSearch.class),
		BackwardSampleSearchBJ("backward SampleSearch with backjumping", null, probcog.bayesnets.inference.BackwardSampleSearchBJ.class),
		BackwardSampleSearchBJPruning("backward SampleSearch with backjumping and backpruning","dev.BackwardSampleSearchBJPruning"),
		Experimental3("an experimental algorithm (usually beta)", "dev.SampleSearch2"),		
		ACE("ACE 2.0 (arithmetic circuits evaluation; requires installation)", null, probcog.bayesnets.inference.ACE.class),
		SampleSearchChoco("SampleSearch that exploits the Choco constraint solver for search", "dev.SampleSearchChoco"),
		QGraphInference("Inference based on counting in the training database", "dev.QGraphInference");
		
		protected String description;
		protected Class<? extends probcog.bayesnets.inference.Sampler> bnClass;
		protected Class<? extends Sampler> blnClass;
		
		private Algorithm(String description, Class<? extends Sampler> blnClass, Class<? extends probcog.bayesnets.inference.Sampler> bnClass) {
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
					bnClass = (Class<? extends probcog.bayesnets.inference.Sampler>)cl;
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
		
		public Sampler createSampler(AbstractGroundBLN gbln) throws ProbCogException {
			Sampler sampler = null;
			if(bnClass != null) {
				sampler = new BNSampler(gbln, bnClass);
			}
			else if(blnClass != null) {	
				Constructor<? extends Sampler> constructor;
				try {
					 constructor = blnClass.getConstructor(gbln.getClass());
					sampler = constructor.newInstance(gbln);
				} 
				catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					throw new ProbCogException("Don't know how to instantiate a sampler for the algorithm '" + toString() + "'", e);
				}
			}
			else
				throw new ProbCogException("Cannot instantiate a sampler for the algorithm '" + toString() + "' - sampler class not found. The algorithm may not be available in your distribution.");
			return sampler;
		}
		
		public static void printList(String indentation) {
			for(Algorithm a : Algorithm.values()) 
				System.out.printf("%s%-28s  %s\n", indentation, a.toString(), a.getDescription());
		}
}