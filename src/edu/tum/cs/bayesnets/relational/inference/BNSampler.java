package edu.tum.cs.bayesnets.relational.inference;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SampledDistribution;

/**
 * Bayesian Network Sampler - reduces inference in relational models to standard Bayesian network inference in the ground network
 * @author jain
 *
 */
public class BNSampler extends Sampler {
	GroundBLN gbln;
	Class<? extends edu.tum.cs.bayesnets.inference.Sampler> samplerClass;
	
	public BNSampler(GroundBLN gbln, Class<? extends edu.tum.cs.bayesnets.inference.Sampler> samplerClass) {
		super(gbln.groundBN);
		this.gbln = gbln;
		this.samplerClass = samplerClass;
	}
	
	public SampledDistribution infer(String[] queries, int numSamples, int infoInterval) throws Exception {
		// create full evidence
		String[][] evidence = this.gbln.db.getEntriesAsArray();
		int[] evidenceDomainIndices = gbln.getFullEvidence(evidence);
	
		// sample
		edu.tum.cs.bayesnets.inference.Sampler sampler = samplerClass.getConstructor(BeliefNetworkEx.class).newInstance(gbln.groundBN);
		sampler.setNumSamples(numSamples);
		sampler.setInfoInterval(infoInterval);
		SampledDistribution dist = sampler.infer(evidenceDomainIndices);
		
		// determine query nodes and print their distributions
		printResults(dist, queries);
		return dist;
	}
}
