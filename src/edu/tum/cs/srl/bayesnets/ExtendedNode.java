package edu.tum.cs.srl.bayesnets;

import java.util.Collection;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

public abstract class ExtendedNode {
	/**
	 * a reference to the BeliefNode that this node extends
	 */
	public BeliefNode node;
	protected RelationalBeliefNetwork bn;
	/**
	 * index into the network's array of BeliefNodes
	 */
	public int index;

	
	public ExtendedNode(RelationalBeliefNetwork rbn, BeliefNode node) {
		this.node = node;
		this.bn = rbn;
		this.index = bn.getNodeIndex(node);
	}
	
	public Collection<DecisionNode> getDecisionParents() {
		BeliefNode[] p = this.bn.bn.getParents(node);
		Collection<DecisionNode> ret = new Vector<DecisionNode>();
		for(int i = 0; i < p.length; i++) {
			ExtendedNode parent = bn.getExtendedNode(p[i]);
			if(parent instanceof DecisionNode)
				ret.add((DecisionNode)parent);
		}
		return ret;
	}
}
