package edu.tum.cs.bayesnets.relational.inference;

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
	
	public BNSampler(AbstractGroundBLN gbln, Class<? extends edu.tum.cs.bayesnets.inference.Sampler> samplerClass) {
		super(gbln.getGroundNetwork());
		this.gbln = gbln;
		this.samplerClass = samplerClass;
	}
	
	public SampledDistribution infer(String[] queries, int numSamples, int infoInterval) throws Exception {
		// create full evidence
		String[][] evidence = this.gbln.getDatabase().getEntriesAsArray();
		int[] evidenceDomainIndices = gbln.getFullEvidence(evidence);
	
		// sample
		edu.tum.cs.bayesnets.inference.Sampler sampler = samplerClass.getConstructor(BeliefNetworkEx.class).newInstance(gbln.getGroundNetwork());
		sampler.setNumSamples(numSamples);
		sampler.setInfoInterval(infoInterval);
		SampledDistribution dist = sampler.infer(evidenceDomainIndices);
		
		// determine query nodes and print their distributions
		printResults(dist, queries);
		return dist;
	}

	@Override
	public String getAlgorithmName() {
		return "BNInference:" + samplerClass.getSimpleName();
	}
}
