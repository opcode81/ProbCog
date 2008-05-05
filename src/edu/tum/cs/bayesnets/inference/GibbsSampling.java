package edu.tum.cs.bayesnets.inference;

import java.util.HashMap;
import java.util.Random;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.SampledDistribution;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.WeightedSample;
import edu.tum.cs.tools.Stopwatch;

public class GibbsSampling extends Sampler {
	int[] nodeOrder;	
	HashMap<BeliefNode, BeliefNode[]> children;
	
	public GibbsSampling(BeliefNetworkEx bn) {
		super(bn);
		children = new HashMap<BeliefNode, BeliefNode[]>();
		BeliefNode[] nodes = bn.bn.getNodes();		
		for(int i = 0; i < nodes.length; i++) {
			children.put(nodes[i], bn.bn.getChildren(nodes[i]));
		}
		nodeOrder = bn.getTopologicalOrder();
	}
	
	public SampledDistribution infer(int[] evidenceDomainIndices, int numSamples, int infoInterval) throws Exception {
		Stopwatch sw = new Stopwatch();
		createDistribution();		
		Random generator = new Random();

		// get initial setting with non-zero probability
		System.out.println("initial setting...");
		WeightedSample s = bn.getWeightedSample(nodeOrder, evidenceDomainIndices, generator);
		
		// do Gibbs sampling
		System.out.println("Gibbs sampling...");
		sw.start();
		BeliefNode[] nodes = bn.bn.getNodes();
		s.trials = 1;
		s.weight = 1;
		// - get a bunch of samples
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				System.out.println("  step " + i);
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
			addSample(s);
		}
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/step)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep()));
		return dist;
	}
}
