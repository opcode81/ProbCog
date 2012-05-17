package probcog.bayesnets.inference;

import probcog.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.util.Stopwatch;

/**
 * @author jain
 */
public class LikelihoodWeighting extends Sampler {
	int[] nodeOrder;
	
	public LikelihoodWeighting(BeliefNetworkEx bn) throws Exception {
		super(bn);		
	}
	
	@Override
	protected void _initialize() {
		nodeOrder = bn.getTopologicalOrder();
	}
	
	@Override
	public void _infer() throws Exception {
		// sample
		Stopwatch sw = new Stopwatch();
		out.println("sampling...");
		sw.start();
		WeightedSample s = new WeightedSample(bn);
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				out.println("  step " + i);			
			WeightedSample ret = getWeightedSample(s, nodeOrder, evidenceDomainIndices); 
			if(ret != null) {
				addSample(ret);
				
				/*
				if(false) { // debugging of weighting
					out.print("w=" + ret.weight);
					for(int j = 0; j < evidenceDomainIndices.length; j++)
						if(evidenceDomainIndices[j] == -1) {
							BeliefNode node = nodes[j];							
							out.print(" " + node.getName() + "=" + node.getDomain().getName(s.nodeDomainIndices[j]));
						}
					out.println();
				}
				*/
			}
			if(converged())
				break;
		}
		sw.stop();
		SampledDistribution dist = distributionBuilder.getDistribution();
		out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/sample, %d samples)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep(), dist.steps));
	}
	
	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
		s.trials = 0;
		boolean successful = false;
loop:	while(!successful) {
			s.weight = 1.0;
			s.trials++;
			if(maxTrials > 0 && s.trials > this.maxTrials) {
				if(!this.skipFailedSteps)
					throw new Exception("Could not obtain a countable sample in the maximum allowed number of trials (" + maxTrials + ")");
				else
					return null;
			}
			// assign values to the nodes in order
			for(int i=0; i < nodeOrder.length; i++) {
				int nodeIdx = nodeOrder[i];
				int domainIdx = evidenceDomainIndices[nodeIdx];
				// for evidence nodes, adjust the weight
				if(domainIdx >= 0) { 
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
					if(prob == 0.0) {
						if(debug)
							out.println("!!! evidence probability was 0 at node " + nodes[nodeIdx]);
						continue loop;
					}
					s.weight *= prob;
				} 
				// for non-evidence nodes, do forward sampling
				else {
					domainIdx = sampleForward(nodes[nodeIdx], s.nodeDomainIndices);
					if(domainIdx < 0) {
						if(debug)
							out.println("!!! could not sample forward because of column with only 0s in CPT of " + nodes[nodeIdx].getName());
						bn.removeAllEvidences();
						continue loop;
					}
					s.nodeDomainIndices[nodeIdx] = domainIdx;
				}
			}
			successful = true;
		}
		return s;		
	}
}
