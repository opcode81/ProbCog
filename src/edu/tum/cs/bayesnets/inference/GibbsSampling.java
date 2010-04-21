package edu.tum.cs.bayesnets.inference;

import java.util.HashMap;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.util.Stopwatch;

public class GibbsSampling extends Sampler {
	int[] nodeOrder;	
	HashMap<BeliefNode, BeliefNode[]> children;
	
	public GibbsSampling(BeliefNetworkEx bn) throws Exception {
		super(bn);
		children = new HashMap<BeliefNode, BeliefNode[]>();
		for(int i = 0; i < nodes.length; i++) {
			children.put(nodes[i], bn.bn.getChildren(nodes[i]));
		}
		nodeOrder = bn.getTopologicalOrder();
	}
	
	public SampledDistribution _infer() throws Exception {
		Stopwatch sw = new Stopwatch();
		createDistribution();		

		// get initial setting with non-zero evidence probability
		System.out.println("initial setting...");
		WeightedSample s = bn.getWeightedSample(nodeOrder, evidenceDomainIndices, generator);
		if(s == null)
			throw new Exception("Could not find an initial state with non-zero probability in given number of trials.");
		
		// do Gibbs sampling
		System.out.println("Gibbs sampling...");
		sw.start();		
		// - get a bunch of samples
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				System.out.println("  step " + i);
			gibbsStep(evidenceDomainIndices, s);
			s.trials = 1;
			s.weight = 1;
			addSample(s);
		}

		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/step)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep()));
		return dist;
	}
	
	public void gibbsStep(int[] evidenceDomainIndices, WeightedSample s) {
		// resample all of the (non-evidence) nodes
		for(int j = 0; j < nodes.length; j++)  {
			// skip evidence nodes
			if(evidenceDomainIndices[j] != -1)
				continue;
			// initialize
			BeliefNode n = nodes[j];
			Discrete dom = (Discrete)n.getDomain();
			int domSize = dom.getOrder();
			double[] distribution = new double[domSize];
			// for the current node, calculate a value for each setting
			for(int d = 0; d < domSize; d++) {
				s.nodeDomainIndices[j] = d;
				// consider the probability of the setting given the node's parents
				double value = getCPTProbability(n, s.nodeDomainIndices);
				// consider the probability of the children's settings given the respective parents					
				for(BeliefNode child : children.get(n)) {
					value *= getCPTProbability(child, s.nodeDomainIndices);
				}			
				distribution[d] = value;
			}
			s.nodeDomainIndices[j] = sample(distribution, generator);
		}
	}
}
