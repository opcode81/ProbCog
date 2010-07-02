package edu.tum.cs.bayesnets.inference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.Discretized;

/**
 * An instance of class <code>WeightedSample</code> represents a weighted
 * sample. It contains the mapping from node to the corresponding value and
 * the value of the sample.
 * 
 * @see BeliefNetworkEx#getWeightedSample(String[][], Random)
 */
public class WeightedSample {
	BeliefNetworkEx bn;
	/**
	 * The mapping from intern numbering to value as index into the domain
	 * of the node.
	 */
	public int[] nodeDomainIndices;
	/**
	 * The mapping from intern numbering to the node index of the outer
	 * class {@link BeliefNetworkEx}.
	 */
	public int[] nodeIndices;
	/**
	 * The weight of the sample.
	 */
	public double weight;
	/**
	 * the number of trials/restarts/backtrackings was required to obtain the sample;
	 */
	public int trials;
	/**
	 * the number of operations used to obtain the sample
	 */
	public int operations;	

	/**
	 * Constructs a weighted sample from given node value mapping and
	 * weight.
	 * 
	 * @param nodeDomainIndices
	 *            the mapping from intern numbering to value as index into
	 *            the domain of the node.
	 * @param weight
	 *            the weight of the sample.
	 * @param nodeIndices
	 *            the mapping from intern numbering to the node index of the
	 *            outer class.
	 *            (may be null, in which case the identity mapping is assumed)
	 * @param trials
	 *            the number of steps that was required to obtain this sample
	 */
	public WeightedSample(BeliefNetworkEx bn, int[] nodeDomainIndices, double weight, int[] nodeIndices, int trials) {
		this.bn = bn;
		if (nodeIndices == null) {
			int numNodes = nodeDomainIndices.length;
			nodeIndices = new int[numNodes];
			for (int i = 0; i < numNodes; i++) {
				nodeIndices[i] = i;
			}
		}
		this.nodeIndices = nodeIndices;
		this.nodeDomainIndices = nodeDomainIndices;
		assert nodeIndices.length == nodeDomainIndices.length;
		this.weight = weight;
		this.trials = trials;
	}
	
	/**
	 * constructs an empty sample with initial weight 1.0 and 0 trials, with an identity node mapping
	 * @param bn	the Bayesian network this sample is for
	 */
	public WeightedSample(BeliefNetworkEx bn) {
		this(bn, new int[bn.bn.getNodes().length], 1.0, null, 0);
	}
	
	public WeightedSample(BeliefNetworkEx bn, int[] domainIndices) {
		this(bn, domainIndices, 1.0, null, 0);
	}

	/**
	 * Extract a sub sample of this sample for the given nodes. The weight
	 * of the sample has to be normalised afterwards and is only meaningful
	 * with the same node base!
	 * 
	 * @param queryNodes
	 *            the nodes to be extracted.
	 * @return the sub sample for the given nodes.
	 */
	public WeightedSample subSample(int[] queryNodes) {
		//BeliefNetworkEx.logger.debug(Arrays.toString(nodeDomainIndices));
		int[] resultIndices = new int[queryNodes.length];
		for (int i = 0; i < queryNodes.length; i++) {
			resultIndices[i] = nodeDomainIndices[queryNodes[i]];
		}
		return new WeightedSample(bn, resultIndices, weight, queryNodes, 1);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(nodeDomainIndices);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof WeightedSample))
			return false;
		return Arrays.equals(nodeDomainIndices,
				(((WeightedSample) obj).nodeDomainIndices));
	}

	/**
	 * Get the assignment from node names to the values. Sometimes we don't
	 * want to use the internal numbering outside this class.
	 * 
	 * @return the assignments of this sample.
	 */
	public Map<String, String> getAssignmentMap() {
		Map<String, String> result = new HashMap<String, String>();

		BeliefNode[] nodes = bn.bn.getNodes();
		for (int i = 0; i < nodeIndices.length; i++) {
			try {
				result.put(nodes[nodeIndices[i]].getName(),
						nodes[nodeIndices[i]].getDomain().getName(
								nodeDomainIndices[i]));
			} catch (RuntimeException e) {
				e.printStackTrace();
				throw e;
			}
		}

		return result;
	}

	/**
	 * Get the assignment from node names to values but use an example value
	 * for {@link Discretized} domains.
	 * 
	 * @return the assignments of this sample probably with example values.
	 */
	public Map<String, String> getUndiscretizedAssignmentMap() {
		Map<String, String> result = new HashMap<String, String>();

		BeliefNode[] nodes = bn.bn.getNodes();
		for (int i = 0; i < nodeIndices.length; i++) {
			try {
				Domain nodeDomain = nodes[nodeIndices[i]].getDomain();
				String value = nodeDomain.getName(nodeDomainIndices[i]);
				if (nodeDomain instanceof Discretized) {
					value = String.valueOf(((Discretized) nodeDomain)
							.getExampleValue(nodeDomainIndices[i]));
				}
				result.put(nodes[nodeIndices[i]].getName(), value);
			} catch (RuntimeException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "WeightedSample(" + getAssignmentMap() + ", " + weight + ")";
	}

	/**
	 * Get only a shorter String containing only the the domain indices
	 * instead of the full assignment map.
	 * 
	 * @return a short string representation.
	 */
	public String toShortString() {
		return "WeightedSample(" + Arrays.toString(nodeDomainIndices)
				+ ", " + weight + ")";
	}

	/**
	 * Check if all query assignments are this sample's assignments.
	 * 
	 * @param queries
	 *            the assignments to be tested.
	 * @return  true if all the assignments are correct,
	 * 	       false otherwise
	 */
	public boolean checkAssignment(String[][] queries) {
		int[] indices = bn.getNodeDomainIndicesFromStrings(queries);
		for (int nodeIndex : nodeIndices) {
			if (indices[nodeIndex] >= 0
					&& indices[nodeIndex] != nodeDomainIndices[nodeIndex])
				return false;
		}
		return true;
	}
	
	public String getCPDLookupString(BeliefNode node) {
		BeliefNode[] domain_product = node.getCPF().getDomainProduct();
		StringBuffer cond = new StringBuffer();
		for(int i = 0; i < domain_product.length; i++) {
			if(i > 0)
				cond.append(", ");
			cond.append(domain_product[i].getName()).append(" = ");
			cond.append(domain_product[i].getDomain().getName(nodeDomainIndices[this.bn.getNodeIndex(domain_product[i])]));
		}
		return cond.toString();
	}
}