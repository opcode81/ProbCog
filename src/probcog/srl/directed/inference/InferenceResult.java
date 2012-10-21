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

import probcog.bayesnets.inference.SampledDistribution;
import probcog.inference.BasicSampledDistribution;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;

public class InferenceResult implements Comparable<InferenceResult> {
	public String varName;
	public String[] domainElements;
	public double[] probabilities;
	public Object[] additionalInfo = null;
	/** the index of the query for which this result was computed **/
	public int queryNo = -1; 
	
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
