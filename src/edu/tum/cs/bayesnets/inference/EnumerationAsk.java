package edu.tum.cs.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.util.Stopwatch;

public class EnumerationAsk extends Sampler {
	int[] nodeOrder;
	int numPathsPruned;
	double numWorldsPruned, numWorldsCounted;
	Stopwatch timer;
	/**
	 * total number of possible worlds
	 */
	double numTotalWorlds;
	
	public EnumerationAsk(BeliefNetworkEx bn) throws Exception {
		super(bn);
		nodeOrder = bn.getTopologicalOrder();
		numTotalWorlds = bn.getNumWorlds();
	}
	
	public void _infer() throws Exception {
		Stopwatch sw = new Stopwatch();
		numPathsPruned = 0;
		numWorldsPruned = numWorldsCounted = 0;
		if(verbose) out.printf("enumerating %s worlds...\n", numTotalWorlds);
		sw.start();
		WeightedSample s = new WeightedSample(bn);
		timer = new Stopwatch();
		timer.start();		
		enumerateWorlds(s, nodeOrder, evidenceDomainIndices, 0, 1); 
		sw.stop();
		report(String.format("\ntime taken: %.2fs (%d worlds enumerated, %d paths pruned)\n", sw.getElapsedTimeSecs(), numWorldsCounted, numPathsPruned));
	}
	
	public void enumerateWorlds(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices, int i, double combinationsHandled) throws Exception {
		//out.printf("enum %s, domain size = %d\n", nodes[nodeOrder[i]].getName(), nodes[nodeOrder[i]].getDomain().getOrder());
		// status messages
		if(timer.getElapsedTimeSecs() > 1) {
			double numDone = numWorldsCounted+numWorldsPruned;
			if(verbose) out.printf(" ~ %.4f%% done (%s worlds handled, %d paths pruned)\r", 100.0*numDone/numTotalWorlds, numDone, numPathsPruned);  
			timer = new Stopwatch();
			timer.start();			
		}
		// if we have completed the world, we are done and can add the world as a sample
		if(i == nodes.length) {
			//out.println("counting sample");
			addSample(s);
			numWorldsCounted++;
			return;
		}
		// otherwise continue		 
		int nodeIdx = nodeOrder[i];
		combinationsHandled *= nodes[nodeOrder[i]].getDomain().getOrder();
		int domainIdx = evidenceDomainIndices[nodeIdx];
		// for evidence nodes, adjust the weight
		if(domainIdx >= 0) { 
			s.nodeDomainIndices[nodeIdx] = domainIdx;
			double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
			s.weight *= prob;
			if(prob == 0.0) { // we have reached zero, so we can save us the trouble of further ramifications
				//out.println("zero reached");
				numPathsPruned++;
				numWorldsPruned += numTotalWorlds / combinationsHandled; 
				return;
			}			
			enumerateWorlds(s, nodeOrder, evidenceDomainIndices, i+1, combinationsHandled);
		} 
		// for non-evidence nodes, consider all settings
		else {
			Domain d = nodes[nodeIdx].getDomain();
			int order = d.getOrder();
			//out.println("  enumerating all " + order + " cases for " + nodes[nodeIdx].getName());			
			double weight = s.weight;
			for(int j = 0; j < order; j++) { 
				s.nodeDomainIndices[nodeIdx] = j;
				double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
				if(prob == 0.0) {
					//out.println("zero reached");
					numPathsPruned++;
					numWorldsPruned += numTotalWorlds / combinationsHandled;
					continue;
				}
				s.weight = weight * prob;
				enumerateWorlds(s, nodeOrder, evidenceDomainIndices, i+1, combinationsHandled);
			}
		}			
	}
}
