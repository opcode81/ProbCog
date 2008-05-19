package edu.tum.cs.bayesnets.inference;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.DiscreteEvidence;
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
	
	public int sampleForward(BeliefNode node, int[] nodeDomainIndices) {
		CPF this_cpf = node.getCPF();
		int diff = 0;
		BeliefNode[] domProd = node.getCPF().getDomainProduct();
		// the fist element in the domain product array is the node we are sampling
		int[] logical_query = new int[domProd.length];
		// construct the logical query to start at the right place, i.e. get the addresses of the first two relevant fields
		//System.out.println("Sampling node:" + node.getName());
		for(int i = 1; i < logical_query.length; i++){
			logical_query[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];
		}
		logical_query[0] = 0;
		int real_addy0 = this_cpf.addr2realaddr(logical_query);
		logical_query[0] = 1;
		int real_addy1 = this_cpf.addr2realaddr(logical_query);
		diff = real_addy1 - real_addy0; // diff is the address difference between two consecutive relevant entries
		// get probabilities for outcomes
		double[] cpt_entries = new double[domProd[0].getDomain().getOrder()];
		int addr = real_addy0;
		double cpt_sum = 0;
		for(int i = 0; i < cpt_entries.length; i++){
			cpt_entries[i] = ((ValueDouble)this_cpf.get(addr)).getValue();
			cpt_sum += cpt_entries[i];
			addr += diff;
		}
		// if the column contains only zeros, impossible case -> cannot sample
		if(cpt_sum == 0)
			return -1;
		return sample(cpt_entries, cpt_sum, generator);
	}
}
