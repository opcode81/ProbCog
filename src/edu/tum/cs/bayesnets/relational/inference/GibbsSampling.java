package edu.tum.cs.bayesnets.relational.inference;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx.SampledDistribution;

public class GibbsSampling extends Sampler {
	GroundBLN gbln;
	
	public GibbsSampling(GroundBLN gbln) {
		super(gbln.groundBN);
		this.gbln = gbln;
	}
	
	public SampledDistribution infer(String[] queries, int numSamples, int infoInterval) throws Exception {
		// create full evidence
		String[][] evidence = this.gbln.db.getEntriesAsArray();
		int[] evidenceDomainIndices = gbln.getFullEvidence(evidence);
	
		// sample
		edu.tum.cs.bayesnets.inference.GibbsSampling gs = new edu.tum.cs.bayesnets.inference.GibbsSampling(gbln.groundBN);
		this.dist = gs.infer(evidenceDomainIndices, numSamples, infoInterval);
		
		// determine query nodes and print their distributions
		printResults(queries);
		return dist;
	}
}
