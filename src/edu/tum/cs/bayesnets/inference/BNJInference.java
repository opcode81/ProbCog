/*
 * Created on Oct 15, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.DiscreteEvidence;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

/**
 * general wrapper for BNJ inference algorithms
 * @author jain
 */
public class BNJInference extends Sampler {

	Class<? extends edu.ksu.cis.bnj.ver3.inference.Inference> algorithmClass;
	
	public BNJInference(BeliefNetworkEx bn, Class<? extends edu.ksu.cis.bnj.ver3.inference.Inference> algoClass) throws Exception {
		super(bn);
		this.algorithmClass = algoClass;
	}

	@Override
	public SampledDistribution infer()
			throws Exception {
		// set evidence
		for(int i = 0; i < evidenceDomainIndices.length; i++)
			if(evidenceDomainIndices[i] != -1)
				nodes[i].setEvidence(new DiscreteEvidence(evidenceDomainIndices[i]));
		
		// run inference
		edu.ksu.cis.bnj.ver3.inference.Inference algo = algorithmClass.newInstance();
		algo.run(bn.bn);
		
		// retrieve results
		createDistribution();
		for(int i = 0; i < nodes.length; i++) {
			CPF cpf = algo.queryMarginal(nodes[i]);
			for(int j = 0; j < dist.values[i].length; j++)
				dist.values[i][j] = cpf.getDouble(j);
		}
		dist.Z = 1.0;
		dist.steps = 1;
		dist.trials = 1;

		// remove evidence
		bn.removeAllEvidences();
		
		return dist;
	}
	
}
