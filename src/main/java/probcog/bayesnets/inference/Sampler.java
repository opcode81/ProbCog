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
package probcog.bayesnets.inference;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.inference.BasicSampledDistribution.ConfidenceInterval;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.util.Stopwatch;

public abstract class Sampler implements ITimeLimitedInference, IParameterHandler {
	public BeliefNetworkEx bn;
	public HashMap<BeliefNode, Integer> nodeIndices;
	public Random generator;
	public BeliefNode[] nodes;
	public int[] evidenceDomainIndices;
	protected ParameterHandler paramHandler;
	protected Collection<Integer> queryVars = null;
	protected StringBuffer report = new StringBuffer();
	protected boolean verbose;
	protected PrintStream out;
	protected boolean initialized = false;
	protected IDistributionBuilder distributionBuilder;
	
	/**
	 * general sampler setting: how many samples to pull from the distribution
	 */
	public int numSamples = 1000;
	
	protected int maxTrials = 5000;
	protected boolean skipFailedSteps = false;
	protected Double confidenceIntervalSizeThreshold = null; 
	public double convergenceCheckInterval = 100;
	protected double totalInferenceTime, initTime, inferenceTime;
	
	/**
	 * general sampler setting: after how many samples to display a message that reports the current status 
	 */
	public int infoInterval = 100;
	
	public boolean debug = false;
	
	public Sampler(BeliefNetworkEx bn) throws Exception {	
		this.bn = bn;
		this.nodes = bn.bn.getNodes();
		nodeIndices = new HashMap<BeliefNode, Integer>();
		for(int i = 0; i < nodes.length; i++) {
			nodeIndices.put(nodes[i], i);
		}
		generator = new Random();
		setVerbose(true);
		paramHandler = new ParameterHandler(this);
		paramHandler.add("confidenceIntervalSizeThreshold", "setConfidenceIntervalSizeThreshold");
		paramHandler.add("randomSeed", "setRandomSeed");
		paramHandler.add("verbose", "setVerbose");
	}
	
	protected SampledDistribution createDistribution() throws Exception {
		SampledDistribution dist = new SampledDistribution(bn);
		dist.setDebugMode(debug);
		paramHandler.addSubhandler(dist.getParameterHandler());
		return dist;
	}
	
	protected synchronized void addSample(WeightedSample s) throws Exception {
		// security check: in debug mode, check if sample respects evidence
		if(debug) {
			for(int i = 0; i < evidenceDomainIndices.length; i++)
				if(evidenceDomainIndices[i] >= 0 && s.nodeDomainIndices[i] != evidenceDomainIndices[i])
					throw new Exception("Attempted to add sample to distribution that does not respect evidence");
		}
		// add to distribution builder
		distributionBuilder.addSample(s);
	}
	
	public void setQueryVars(Collection<Integer> queryVars) {
		this.queryVars = queryVars;
		initialized = false;
	}
	
	protected boolean converged() throws Exception {
		if(!(this.distributionBuilder instanceof DirectDistributionBuilder))
			return false;
		SampledDistribution dist = distributionBuilder.getDistribution();
		if(dist.getNumSamples() % this.convergenceCheckInterval != 0)
			return false; // TODO assumes that all algorithms call this method after each step
		// determine convergence based on confidence interval sizes
		if(confidenceIntervalSizeThreshold != null) {
			if(!dist.usesConfidenceComputation())
				throw new Exception("Cannot determine convergence based on confidence interval size: No confidence level specified.");
			double max = 0;
			for(Integer i : queryVars) {
				ConfidenceInterval interval = dist.getConfidenceInterval(i, 0);
				max = Math.max(max, interval.getSize());
			}
			if(max <= confidenceIntervalSizeThreshold) {
				if(verbose) System.out.printf("Convergence criterion reached: maximum confidence interval size = %f\n", max);
				return true;
			}
		}
		return false;
	}
	
	public void setConfidenceIntervalSizeThreshold(double t) {
		confidenceIntervalSizeThreshold = t;
	}
	
	/**
	 * polls the results during time-limited inference
	 * @return
	 * @throws Exception 
	 */
	public synchronized SampledDistribution pollResults() throws Exception {
		if(distributionBuilder == null)
			return null;
		SampledDistribution dist = distributionBuilder.getDistribution();
		if(dist == null)
			return null;
		return dist.clone();
	}
	
	/**
	 * samples from a distribution whose normalization constant is not known
	 * @param distribution
	 * @param generator
	 * @return the index of the value that was sampled (or -1 if the distribution is not well-defined)
	 */
	public static int sample(double[] distribution, Random generator) {
		double sum = 0;
		for(int i = 0; i < distribution.length; i++)
			sum += distribution[i];
		return sample(distribution, sum, generator);
	}
	
	/**
	 * samples from the given distribution
	 * @param distribution
	 * @param sum the distribution's normalization constant
	 * @param generator
	 * @return the index of the value that was sampled (or -1 if the distribution is not well-defined)
	 */
	public static int sample(double[] distribution, double sum, Random generator) {
		double random = generator.nextDouble() * sum;		
		int ret = 0;
		sum = 0;
		int i = 0;
		while(sum < random && i < distribution.length) {			
			sum += distribution[ret = i++];
		}
		return sum >= random ? ret : -1;		
	}
	
	/**
	 * samples from a distribution whose normalization constant is not known
	 * @param distribution 
	 * @param generator
	 * @return the index of the value in the collection that was sampled (or -1 if the distribution is not well-defined)
	 */
	public static int sample(Collection<Double> distribution, Random generator) {
		double sum = 0;
		for(Double d : distribution)
			sum += d;
		return sample(distribution, sum, generator);
	}

	/**
	 * samples from the given distribuion
	 * @param distribution
	 * @param sum the distribution's normalization constant
	 * @param generator
	 * @return the index of the value in the collection that was sampled (or -1 if the distribution is not well-defined)
	 */
	public static int sample(Collection<Double> distribution, double sum, Random generator) {
		double random = generator.nextDouble() * sum;
		if(sum == 0)
			return -1;
		sum = 0;
		int i = 0;
		for(Double d : distribution) {
			sum += d;
			if(sum >= random)
				return i;
			++i;
		}
		return -1;		
	}

	/**
	 * gets the CPT entry of the given node for the configuration of parents that is provided in the array of domain indices
	 * @param node
	 * @param nodeDomainIndices domain indices for each node in the network (only the parents of 'node' are required to be set) 
	 * @return the probability value
	 */
	protected double getCPTProbability(BeliefNode node, int[] nodeDomainIndices) {
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		for(int i = 0; i < addr.length; i++)
			addr[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];
		return cpf.getDouble(addr);
	}
	
	public void setNumSamples(int numSamples) {
		this.numSamples = numSamples;
	}
	
	public void setInfoInterval(int infoInterval) {
		this.infoInterval = infoInterval;
	}
	
	public void setMaxTrials(int maxTrials) {
		this.maxTrials = maxTrials;
	}
	
	public void setSkipFailedSteps(boolean canSkip) {
		this.skipFailedSteps = canSkip;
	}
	
	public void setEvidence(int[] evidenceDomainIndices) throws Exception {
		this.evidenceDomainIndices = evidenceDomainIndices;
		initialized = false;
	}
	
	public void setRandomSeed(int seed) {
		generator.setSeed(seed);
	}
	
	protected abstract void _infer() throws Exception;
	protected void _initialize() throws Exception {}
	
	/**
	 * initializes the inference method such that inference can be run
	 */
	public final void initialize() throws Exception {
		Stopwatch sw = new Stopwatch();
		sw.start();
		_initialize();
		distributionBuilder = createDistributionBuilder();
		sw.stop();
		initTime = sw.getElapsedTimeSecs();
		initialized = true;
	}

	/**
	 * runs the actual inference method (initializing first if necessary)
	 */
	public final SampledDistribution infer() throws Exception {
		// initialize
		if(!initialized)
			initialize();
		
		// run inference
		Stopwatch sw = new Stopwatch();
		sw.start();
		_infer();
		inferenceTime = sw.getElapsedTimeSecs();
		
		report(String.format("total inference time: %fs (initialization: %fs; core %fs)\n", getTotalInferenceTime(), getInitTime(), getInferenceTime()));
		if(verbose) out.print(report.toString());
		
		return distributionBuilder.getDistribution();
	}
	
	/**
	 * @return returns the distribution builder that creates the distribution 
	 * based on weighted samples
	 * @throws Exception 
	 */
	protected IDistributionBuilder createDistributionBuilder() throws Exception {
		return new DirectDistributionBuilder(createDistribution());
	}
	
	/**
     * @return the time taken for the inference process in seconds
	 */
	public double getTotalInferenceTime() {
		return getInferenceTime() + getInitTime();
	}
	
	public double getInferenceTime() {
		return inferenceTime;		
	}
	
	public double getInitTime() {
		return initTime;
	}
	
	/**
	 * samples forward, i.e. samples a value for 'node' given its parents
	 * @param node  the node for which to sample a value
	 * @param nodeDomainIndices  array of domain indices for all nodes in the network; the values for the parents of 'node' must be set already
	 * @return  the index of the domain element of 'node' that is sampled, or -1 if sampling is impossible because all entries in the relevant column are 0 
	 */
	protected int sampleForward(BeliefNode node, int[] nodeDomainIndices) {
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		// get the addresses of the first two relevant fields and the difference between them
		for(int i = 1; i < addr.length; i++)
			addr[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];		
		addr[0] = 0; // (the first element in the index into the domain of the node we are sampling)
		int realAddr = cpf.addr2realaddr(addr);
		addr[0] = 1;
		int diff = cpf.addr2realaddr(addr) - realAddr; // diff is the address difference between two consecutive entries in the relevant column
		// get probabilities for outcomes
		double[] cpt_entries = new double[domProd[0].getDomain().getOrder()];
		double sum = 0;
		for(int i = 0; i < cpt_entries.length; i++){
			cpt_entries[i] = cpf.getDouble(realAddr);
			sum += cpt_entries[i];
			realAddr += diff;
		}
		// if the column contains only zeros, it is an impossible case -> cannot sample
		if(sum == 0)
			return -1;
		return sample(cpt_entries, sum, generator);
	}
	
	public double[] getConditionalDistribution(BeliefNode node, int[] nodeDomainIndices) {
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		// get the addresses of the first two relevant fields and the difference between them
		for(int i = 1; i < addr.length; i++)
			addr[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];		
		addr[0] = 0; // (the first element in the index into the domain of the node we are sampling)
		int realAddr = cpf.addr2realaddr(addr);
		addr[0] = 1;
		int diff = cpf.addr2realaddr(addr) - realAddr; // diff is the address difference between two consecutive entries in the relevant column
		// get probabilities for outcomes
		double[] cpt_entries = new double[domProd[0].getDomain().getOrder()];
		for(int i = 0; i < cpt_entries.length; i++){
			cpt_entries[i] = cpf.getDouble(realAddr);
			realAddr += diff;
		}
		return cpt_entries;
	}
	
	public int getNodeIndex(BeliefNode node) {
		return nodeIndices.get(node);
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
		if(verbose)
			out = System.out;
		else
			out = new PrintStream(new java.io.OutputStream() { public void write(int b){} });
	}
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	/**
	 * adds a string to the report that is displayed after the inference procedure has returned 
	 * @param s
	 */
	protected void report(String s) {
		this.report.append(s);
		this.report.append('\n');
	}
}
