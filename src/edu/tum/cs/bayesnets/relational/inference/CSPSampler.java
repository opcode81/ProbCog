package edu.tum.cs.bayesnets.relational.inference;

import java.util.Random;

import org.python.core.PyObject.ConversionException;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.SampledDistribution;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.WeightedSample;
import edu.tum.cs.bayesnets.relational.core.bln.GroundFormula;
import edu.tum.cs.bayesnets.util.TopologicalOrdering;
import edu.tum.cs.bayesnets.util.TopologicalSort;
import edu.tum.cs.tools.Stopwatch;

public class CSPSampler extends Sampler {
	GroundBLN gbln;
	
	public CSPSampler(GroundBLN gbln) {
		super(gbln.groundBN);
		this.gbln = gbln;
	}
	
	public SampledDistribution infer(String[] queries, int numSamples, int infoInterval) throws ConversionException {
		// create full evidence
		String[][] evidence = this.gbln.db.getEntriesAsArray();
		int[] evidenceDomainIndices = gbln.getFullEvidence(evidence);
		BeliefNetworkEx groundBN = gbln.getGroundNetwork();
		
		// get node ordering
		System.out.println("determining node ordering...");
		TopologicalOrdering nodeOrder = new TopologicalSort(groundBN.bn).run();
		
		// get constraints from hard formulas
		System.out.println("normalizing formulas...");
		for(GroundFormula gf : gbln.bln.iterGroundFormulas()) {
			gf.toCNF();
		}
		
		// sample
		Stopwatch sw = new Stopwatch();
		createDistribution(groundBN);
		
		Random generator = new Random();
		System.out.println("sampling...");
		sw.start();
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				System.out.println("  step " + i);
			
			// obtain a sample
			WeightedSample s = null;
			
			dist.addSample(s);
		}
		
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/step)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep()));
		
		// determine query nodes and print their distributions
		printResults(queries);
		
		return dist;
	}

}
