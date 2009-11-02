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
	EPIS("importance sampling based on evidence prepropagation [SMILE]", SmileEPIS.class), 
	BackwardSampling("backward simulation", BackwardSampling.class),			 
	BackwardSamplingPriors("backward simulation with prior bias", BackwardSamplingWithPriors.class), 
	BackwardSamplingWithChildren("backward simulation with extended context", BackwardSamplingWithChildren.class),
	SmileBackwardSampling("backward simulation [SMILE]", SmileBackwardSampling.class),						 
	SATIS("SAT-IS: satisfiability-based importance sampling", SATIS_BSampler.class), 
	IJGP("Iterative Join-Graph Propagation", IJGP.class),
	EnumerationAsk("Enumeration-Ask (exact, highly inefficient)", EnumerationAsk.class),
	Pearl("Pearl's algorithm for polytrees (exact)", BNJPearl.class),
	SmilePearl("Pearl's algorithm for polytrees (exact) [SMILE]", SmilePearl.class),
	VarElim("variable elimination (exact)", BNJVariableElimination.class),
	Experimental("an experimental algorithm (usually beta)", SampleSearch.class);
	
	String description;
	Class<? extends Sampler> samplerClass; 
	
	private Algorithm(String description, Class<? extends Sampler> samplerClass) {
		this.description = description;
		this.samplerClass = samplerClass;		
	}
	
	public Sampler createSampler(BeliefNetworkEx bn) throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		return samplerClass.getConstructor(bn.getClass()).newInstance(bn);
	}
	
	public String getDescription() {
		return description;
	}
}
