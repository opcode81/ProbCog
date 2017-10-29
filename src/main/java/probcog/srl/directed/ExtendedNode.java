/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.directed;

import java.util.Collection;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

/**
 * Represents a relational extension of a standard belief node appearing
 * in a Bayesian (fragment) network.
 * @author Dominik Jain
 */
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
