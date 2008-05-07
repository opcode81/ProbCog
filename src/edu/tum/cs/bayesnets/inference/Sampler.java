package edu.tum.cs.bayesnets.inference;

import java.util.HashMap;
import java.util.Random;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public abstract class Sampler {
	public BeliefNetworkEx bn;
	public SampledDistribution dist;
	HashMap<BeliefNode, Integer> nodeIndices;
	Random generator;
	/**
	 * general sampler setting: how many samples to pull from the distribution
	 */
	public int numSamples;
	/**
	 * general sampler setting: after how many samples to display a message that reports the current status 
	 */
	public int infoInterval;
	
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
	
	protected double getCPTProbability(BeliefNode node, int[] nodeDomainIndices) {
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		for(int i = 0; i < addr.length; i++)
			addr[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];
		return ((ValueDouble)cpf.get(addr)).getValue();
	}
	
	public void setNumSamples(int numSamples) {
		this.numSamples = numSamples;
	}
	
	public void setInfoInterval(int infoInterval) {
		this.infoInterval = infoInterval;
	}
	
	public abstract SampledDistribution infer(int[] evidenceDomainIndices) throws Exception;
}
