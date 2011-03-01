/*
 * Created on Mar 1, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import java.util.HashMap;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.Variable;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.util.datastruct.Pair;

public class QGraphInference extends Sampler {

	protected int port;
	
	public QGraphInference(AbstractGroundBLN gbln) throws Exception {
		super(gbln);
		this.paramHandler.add("port", "setPort");
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	protected boolean isFixedDomainElement(String domName, String element) {
		HashMap<String, String[]> guaranteedDomElems = gbln.getRBN().getGuaranteedDomainElements();
		return false;
	}
	

	@Override
	protected SampledDistribution _infer() throws Exception {
		BeliefNetworkEx bn = this.gbln.getGroundNetwork();
		BeliefNode[] nodes = bn.getNodes();		
		
		Database db = this.gbln.getDatabase();				
		
		// build initial qgraph from evidence
		for(Variable var : db.getEntries()) {
			System.out.println("evidence: " + var);
			
			Signature sig = gbln.getRBN().getSignature(var.functionName);
			

		}
		
		SampledDistribution dist = new SampledDistribution(bn);
		
		for(Integer idxVar : this.queryVars) {
			BeliefNode var = nodes[idxVar];
			
			Pair<String, String[]> p = Signature.parseVarName(var.getName());
			Signature sig = gbln.getRBN().getSignature(p.first);
			// sig.argTypes[0]; type of first argument of the function
			
			Discrete domain = (Discrete)var.getDomain();
			double sum = 0;
			for(int i = 0; i < domain.getOrder(); i++) {
				// create qgraph query for that domain element
				// run it
				
				double value = 0.5; // number of qgraph matches
				sum += value;
				
				dist.values[idxVar][i] = value;
			}
			dist.Z = sum;
		}
		
		return dist;
	}

}
