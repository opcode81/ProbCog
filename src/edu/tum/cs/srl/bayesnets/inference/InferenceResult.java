package edu.tum.cs.srl.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.inference.BasicSampledDistribution;

public class InferenceResult implements Comparable<InferenceResult> {
	public String varName;
	public String[] domainElements;
	public double[] probabilities;
	public Object[] additionalInfo = null;
	
	public InferenceResult(SampledDistribution dist, int nodeIdx) {
		BeliefNode node = dist.bn.bn.getNodes()[nodeIdx];
		varName = node.getName();
		domainElements = dist.getDomain(nodeIdx);
		probabilities = dist.getDistribution(nodeIdx);
		if(dist.usesConfidenceComputation()) {
			additionalInfo = new BasicSampledDistribution.ConfidenceInterval[domainElements.length];
			for(int i = 0; i < additionalInfo.length; i++)
				additionalInfo[i] = dist.getConfidenceInterval(nodeIdx, i);
		}
	}
	
	public void print() {
		System.out.println(varName + ":");
		if(additionalInfo == null)
			for(int i = 0; i < domainElements.length; i++)
				System.out.println(String.format("  %.4f %s", probabilities[i], domainElements[i]));
		else
			for(int i = 0; i < domainElements.length; i++)
				System.out.println(String.format("  %.4f  %s  %s", probabilities[i], additionalInfo[i], domainElements[i]));
	}

	/**
	 * for ordering inference results according to alphabetical ordering of variable names
	 */
	public int compareTo(InferenceResult o) {
		return varName.compareTo(o.varName);
	}
	
	public int getDomainSize() {
		return domainElements.length;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer(varName + " ~ ");
		for(int i = 0; i < this.domainElements.length; i++) {
			if(i > 0)
				sb.append(", ");
			sb.append(domainElements[i]);
			sb.append(": ");
			sb.append(probabilities[i]);
		}
		return sb.toString();
	}
}
