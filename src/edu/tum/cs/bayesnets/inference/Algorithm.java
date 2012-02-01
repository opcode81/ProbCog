/*
 * Created on Oct 27, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import java.lang.reflect.InvocationTargetException;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public enum Algorithm {
	LikelihoodWeighting("likelihood weighting", LikelihoodWeighting.class), 
	LWU("likelihood weighting with uncertain evidence", LikelihoodWeightingWithUncertainEvidence.class), 
	GibbsSampling("Gibbs sampling (MCMC)", GibbsSampling.class), 
	EPIS("importance sampling based on evidence prepropagation [SMILE]", "edu.tum.cs.bayesnets.inference.SmileEPIS"), 
	BackwardSampling("backward simulation", BackwardSampling.class),			 
	BackwardSamplingPriors("backward simulation with prior bias", BackwardSamplingWithPriors.class), 
	BackwardSamplingWithChildren("backward simulation with extended context", BackwardSamplingWithChildren.class),
	SmileBackwardSampling("backward simulation [SMILE]", "edu.tum.cs.bayesnets.inference.SmileBackwardSampling"),						 
	SATIS("SAT-IS: satisfiability-based importance sampling", SATIS_BSampler.class),
	SampleSearch("SampleSearch: backtracking search for satisfiable states", SampleSearch.class),
	IJGP("Iterative Join-Graph Propagation", IJGP.class),
	BeliefPropagation("Belief Propagation", BeliefPropagation.class),
	EnumerationAsk("Enumeration-Ask (exact, highly inefficient)", EnumerationAsk.class),
	Pearl("Pearl's algorithm for polytrees (exact)", BNJPearl.class),
	SmilePearl("Pearl's algorithm for polytrees (exact) [SMILE]", "edu.tum.cs.bayesnets.inference.SmilePearl"),
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
