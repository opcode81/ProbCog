package edu.tum.cs.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

/**
 * provides an interface to all of the inference algorithms implemented in the SMILE library
 * @author jain
 *
 */
public class SmileInference extends Sampler {
	smile.Network net;
	int algorithmID;
	
	public SmileInference(BeliefNetworkEx bn, int algorithmID) throws Exception {
		super(bn);
		this.algorithmID = algorithmID;
		
		// obtain SMILE Network
		System.out.println("converting network...");
		bn.save("temp.net", IOPlugInLoader.getInstance().GetExportersByExt(".net"));
		net = new smile.Network();
		net.readFile("temp.net");
	//	new java.io.File("temp.net").delete();
	}

	public SampledDistribution _infer() throws Exception {
		net.setBayesianAlgorithm(algorithmID);
		net.setSampleCount(numSamples);
		
		// set evidence
		System.out.println("setting evidence...");
		for(int i = 0; i < evidenceDomainIndices.length; i++) {
			if(evidenceDomainIndices[i] != -1)
				net.setEvidence("N" + i, evidenceDomainIndices[i]);
		}
		
		// inference
		System.out.println("sampling...");
		net.updateBeliefs();
		
		// store results in distribution
		System.out.println("reading results...");
		SampledDistribution dist = createDistribution();
		for(int i = 0; i < nodes.length; i++) {			
			double[] values = net.getNodeValue("N" + i);
			dist.values[i] = values;
		}
		dist.Z = 1.0;
		((ImmediateDistributionBuilder)distributionBuilder).setDistribution(dist);
		
		return dist;
	}
	
	protected IDistributionBuilder createDistributionBuilder() {
		return new ImmediateDistributionBuilder();
	}
}
