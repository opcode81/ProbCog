package edu.tum.cs.bayesnets.relational.inference;

import java.util.Vector;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.bayesnets.relational.core.bln.AbstractGroundBLN;

/**
 * Bayesian Network Sampler - reduces inference in relational models to standard Bayesian network inference in the ground network
 * @author jain
 *
 */
public class BNSampler extends Sampler {
	AbstractGroundBLN gbln;
	Class<? extends edu.tum.cs.bayesnets.inference.Sampler> samplerClass;
	protected int maxTrials;
	/**
	 * whether steps that exceed the max number of trials should just be skipped rather than raising an exception
	 */
	protected boolean skipFailedSteps;
		
	public BNSampler(AbstractGroundBLN gbln, Class<? extends edu.tum.cs.bayesnets.inference.Sampler> samplerClass) {
		this.gbln = gbln;
		this.samplerClass = samplerClass;
		maxTrials = 5000;
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
		edu.tum.cs.bayesnets.inference.Sampler sampler = samplerClass.getConstructor(BeliefNetworkEx.class).newInstance(gbln.getGroundNetwork());
		sampler.setDebugMode(debug);
		sampler.setNumSamples(numSamples);
		sampler.setInfoInterval(infoInterval);
		sampler.setMaxTrials(maxTrials);
		sampler.setSkipFailedSteps(skipFailedSteps);
		SampledDistribution dist = sampler.infer(evidenceDomainIndices);
		
		// determine query nodes and print their distributions		
		return getResults(dist, queries);
	}

	@Override
	public String getAlgorithmName() {
		return "BNInference:" + samplerClass.getSimpleName();
	}
}
