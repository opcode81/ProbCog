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
package probcog.bayesnets.inference;

import java.lang.reflect.InvocationTargetException;



import probcog.bayesnets.core.BeliefNetworkEx;

/**
 * Enumeration of Bayesian network inference algorithms.
 * 
 * @author Dominik Jain
  */
public enum Algorithm {
	
	// NOTE: Smile inference is not included in this distribution due to licensing restrictions
	
	LikelihoodWeighting("likelihood weighting", LikelihoodWeighting.class), 
	GibbsSampling("Gibbs sampling (MCMC)", GibbsSampling.class), 
	//EPIS("importance sampling based on evidence prepropagation [SMILE]", "edu.tum.cs.bayesnets.inference.SmileEPIS"), 
	BackwardSampling("backward simulation", BackwardSampling.class),			 
	BackwardSamplingPriors("backward simulation with prior bias", BackwardSamplingWithPriors.class), 
	BackwardSamplingWithChildren("backward simulation with extended context", BackwardSamplingWithChildren.class),
	//SmileBackwardSampling("backward simulation [SMILE]", "edu.tum.cs.bayesnets.inference.SmileBackwardSampling"),						 
	SATIS("SAT-IS: satisfiability-based importance sampling", SATIS_BSampler.class),
	SampleSearch("SampleSearch: backtracking search for satisfiable states", SampleSearch.class),
	SampleSearchBJ("SampleSearch with backjumping", SampleSearchBJ.class),
	SampleSearchBJLearning("SampleSearch with backjumping and constraint learning", SampleSearchBJLearning.class),
	IJGP("Iterative Join-Graph Propagation", IJGP.class),
	BeliefPropagation("Belief Propagation", BeliefPropagation.class),
	EnumerationAsk("Enumeration-Ask (exact, highly inefficient)", EnumerationAsk.class),
	Pearl("Pearl's algorithm for polytrees (exact)", BNJPearl.class),
	//SmilePearl("Pearl's algorithm for polytrees (exact) [SMILE]", "edu.tum.cs.bayesnets.inference.SmilePearl"),
	//VarElim("variable elimination (exact)", BNJVariableElimination.class),
	VarElim("variable elimination (exact)", VariableElimination.class),
	BackwardSampleSearch("Backward SampleSearch", BackwardSampleSearch.class),
	BackwardSampleSearchBJ("Backward SampleSearch with backjumping", BackwardSampleSearchBJ.class),
	//BackwardSampleSearchIB("Backward SampleSearch with intelligent backtracking","dev.BackwardSampleSearchIB"),
	ACE("ACE 2.0 (arithmetic circuits evaluation; requires installation)", ACE.class);
	
	String description;
	Class<? extends Sampler> samplerClass; 
	
	private Algorithm(String description, Class<? extends Sampler> samplerClass) {
		this.description = description;
		this.samplerClass = samplerClass;		
	}

	@SuppressWarnings("unchecked")
	private Algorithm(String description, String className) {
		this.description = description;
		try {
			this.samplerClass = (Class<? extends Sampler>) Class.forName(className);
		} 
		catch(ClassNotFoundException e) {
		}		
	}
	
	public Sampler createSampler(BeliefNetworkEx bn) throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		return samplerClass.getConstructor(bn.getClass()).newInstance(bn);
	}
	
	public String getDescription() {
		return description;
	}
}
