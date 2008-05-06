package edu.tum.cs.bayesnets.inference;


public interface IInferenceAlgorithm {
	public SampledDistribution infer(int[] evidenceDomainIndices, int numSamples, int infoInterval) throws Exception;
}
