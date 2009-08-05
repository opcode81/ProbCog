package edu.tum.cs.srl.bayesnets.inference;

import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;

/**
 * Bayesian Network Sampler - reduces inference in relational models to standard Bayesian network inference in the ground network
 * @author jain
 *
 */
public class BNSampler extends Sampler {
	AbstractGroundBLN gbln;
	protected int maxTrials;
	/**
	 * whether steps that exceed the max number of trials should just be skipped rather than raising an exception
	 */
	protected boolean skipFailedSteps;
	protected Class<? extends edu.tum.cs.bayesnets.inference.Sampler> samplerClass;
		
	public BNSampler(AbstractGroundBLN gbln, Class<? extends edu.tum.cs.bayesnets.inference.Sampler> samplerClass) {
		this.gbln = gbln;
		maxTrials = 5000;
		this.samplerClass = samplerClass;
	}
	
	public void setMaxTrials(int maxTrials) {
		this.maxTrials = maxTrials; 
	}
	
	public void setSkipFailedSteps(boolean canSkip) {
		this.skipFailedSteps = canSkip;
	}
	
	@Override
	public Vector<InferenceResult> infer(Iterable<String> queries, int numSamples, int infoInterval) throws Exception {
		// create full evidence
		String[][] evidence = this.gbln.getDatabase().getEntriesAsArray();
		int[] evidenceDomainIndices = gbln.getFullEvidence(evidence);
	
		// sample		
		edu.tum.cs.bayesnets.inference.Sampler sampler = getSampler();
		sampler.setDebugMode(debug);
		sampler.setNumSamples(numSamples);
		sampler.setInfoInterval(infoInterval);
		sampler.setMaxTrials(maxTrials);
		sampler.setSkipFailedSteps(skipFailedSteps);
		SampledDistribution dist = sampler.infer(evidenceDomainIndices);
		
		// determine query nodes and print their distributions		
		return getResults(dist, queries);
	}
	
	protected edu.tum.cs.bayesnets.inference.Sampler getSampler() throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		return samplerClass.getConstructor(BeliefNetworkEx.class).newInstance(gbln.getGroundNetwork());	
	}

	@Override
	public String getAlgorithmName() {
		return "BNInference:" + samplerClass.getSimpleName();
	}
}
