/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.inference.ITimeLimitedInference;
import probcog.bayesnets.inference.SampledDistribution;
import probcog.srl.directed.bln.AbstractGroundBLN;

/**
 * Bayesian Network Sampler: reduces inference in relational models to standard Bayesian network 
 * inference in the ground (auxiliary) network.
 * @author Dominik Jain
 */
public class BNSampler extends Sampler implements ITimeLimitedInference {
	protected int maxTrials;
	/**
	 * whether steps that exceed the max number of trials should just be skipped rather than raising an exception
	 */
	protected boolean skipFailedSteps;
	protected Class<? extends probcog.bayesnets.inference.Sampler> samplerClass;
	protected probcog.bayesnets.inference.Sampler sampler;
	/**
	 * the evidence we are working on
	 */
	protected int[] evidenceDomainIndices;
		
	public BNSampler(AbstractGroundBLN gbln, Class<? extends probcog.bayesnets.inference.Sampler> samplerClass) throws Exception {
		super(gbln);
		maxTrials = 5000;
		this.paramHandler.add("maxTrials", "setMaxTrials");
		this.paramHandler.add("skipFailedSteps", "setSkipFailedSteps");
		this.samplerClass = samplerClass;
	}
	
	public void setMaxTrials(int maxTrials) {
		this.maxTrials = maxTrials; 
	}
	
	public void setSkipFailedSteps(boolean canSkip) {
		this.skipFailedSteps = canSkip;
	}
	
	@Override
	protected void _initialize() throws Exception {
		// create full evidence
		String[][] evidence = this.gbln.getDatabase().getEntriesAsArray();
		evidenceDomainIndices = gbln.getFullEvidence(evidence);
	
		// initialize sampler				
		sampler = getSampler();
		paramHandler.addSubhandler(sampler.getParameterHandler());
		sampler.setEvidence(evidenceDomainIndices);
		sampler.setQueryVars(queryVars);
		sampler.setDebugMode(debug);
		sampler.setNumSamples(numSamples);
		sampler.setInfoInterval(infoInterval);
		sampler.setMaxTrials(maxTrials);
		sampler.setSkipFailedSteps(skipFailedSteps);
		sampler.initialize();
	}
	
	@Override
	public SampledDistribution _infer() throws Exception {
		// run inference
		if(verbose) System.out.printf("running %s...\n", sampler.getAlgorithmName());
		SampledDistribution dist = sampler.infer();
		return dist;
	}
	
	protected probcog.bayesnets.inference.Sampler getSampler() throws Exception {
		return samplerClass.getConstructor(BeliefNetworkEx.class).newInstance(gbln.getGroundNetwork());	
	}

	@Override
	public String getAlgorithmName() {
		return "BNInference:" + samplerClass.getSimpleName();
	}
	
	public SampledDistribution pollResults() throws Exception {
		if(sampler == null)
			return null;
		return sampler.pollResults();
	}
}
