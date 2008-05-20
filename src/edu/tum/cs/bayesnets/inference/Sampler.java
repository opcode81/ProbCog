package edu.tum.cs.bayesnets.inference;

import java.util.Collection;
import java.util.HashMap;
import java.util.Random;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public abstract class Sampler {
	protected BeliefNetworkEx bn;
	protected SampledDistribution dist;
	protected HashMap<BeliefNode, Integer> nodeIndices;
	protected Random generator;
	
	/**
	 * general sampler setting: how many samples to pull from the distribution
	 */
	public int numSamples = 1000;
	
	/**
	 * general sampler setting: after how many samples to display a message that reports the current status 
	 */
	public int infoInterval = 100;
	
	public Sampler(BeliefNetworkEx bn) {	
		this.bn = bn;
		nodeIndices = new HashMap<BeliefNode, Integer>();
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			nodeIndices.put(nodes[i], i);
		}
		generator = new Random();
	}
	
	protected void createDistribution() {
		this.dist = new SampledDistribution(bn);
	}
	
	protected void addSample(WeightedSample s) {
		this.dist.addSample(s);
	}
	
	public static int sample(double[] distribution, Random generator) {
		double sum = 0;
		for(int i = 0; i < distribution.length; i++)
			sum += distribution[i];
		return sample(distribution, sum, generator);
	}
	
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
	
	public static int sample(Collection<Double> distribution, Random generator) {
		double sum = 0;
		for(Double d : distribution)
			sum += d;
		return sample(distribution, sum, generator);
	}

	public static int sample(Collection<Double> distribution, double sum, Random generator) {
		double random = generator.nextDouble() * sum;		
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
	
	public abstract SampledDistribution infer(int[] evidenceDomainIndices) throws Exception;
	
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
		addr[0] = 0; // (the fist element in the index into the domain of the node we are sampling)
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
	
	public int getNodeIndex(BeliefNode node) {
		return nodeIndices.get(node);
	}
}
