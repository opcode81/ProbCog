package edu.tum.cs.bayesnets.inference;

import java.io.FileNotFoundException;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
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
	
	public SmileInference(BeliefNetworkEx bn, int algorithmID) throws FileNotFoundException {
		super(bn);
		this.algorithmID = algorithmID;
		
		// obtain SMILE Network
		System.out.println("converting network...");
		bn.save("temp.net", IOPlugInLoader.getInstance().GetExportersByExt(".net"));
		net = new smile.Network();
		net.readFile("temp.net");
		new java.io.File("temp.net").delete();
	}

	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {
		net.setBayesianAlgorithm(algorithmID);
		
		// set evidence
		System.out.println("setting evidence...");
		for(int i = 0; i < evidenceDomainIndices.length; i++) {
			if(evidenceDomainIndices[i] != -1)
				net.setEvidence("N" + i, evidenceDomainIndices[i]);
		}
		
		// inference
		System.out.println("EPIS sampling...");
		net.updateBeliefs();
		
		// store results in distribution
		System.out.println("reading results...");
		this.createDistribution();
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {			
			double[] values = net.getNodeValue("N" + i);
			dist.sums[i] = values;
		}
		dist.Z = 1.0;
		
		return dist;
	}	
}
