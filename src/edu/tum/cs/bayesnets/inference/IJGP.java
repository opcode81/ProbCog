/*
 * Created on Sep 29, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class IJGP extends Sampler {

	public IJGP(BeliefNetworkEx bn) {
		super(bn);
		
		// construct join-graph
	}

	@Override
	public SampledDistribution infer(int[] evidenceDomainIndices)
			throws Exception {
		createDistribution();
		
		// do it
		
		return dist;
	}
		
	protected static class JoinGraph {
		public JoinGraph(BeliefNetworkEx bn) {
			// construct join-graph
		}
		
		protected Node merge(Node u, Node v) {
			return null;
		}
		
		public static class Node {
			Vector<CPF> functions = new Vector<CPF>();
			HashSet<BeliefNode> nodes = new HashSet<BeliefNode>();
		}
	}
}
