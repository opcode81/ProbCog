/*******************************************************************************
 * Copyright (C) 2007-2012 Dominik Jain.
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
package probcog.srl.directed.learning;

import java.util.Map.Entry;
import java.util.Set;

import probcog.exception.ProbCogException;
import probcog.srl.BooleanDomain;
import probcog.srl.GenericDatabase;
import probcog.srl.Signature;
import probcog.srl.directed.DecisionNode;
import probcog.srl.directed.ExtendedNode;
import probcog.srl.directed.RelationalBeliefNetwork;
import probcog.srl.directed.RelationalNode;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.util.datastruct.Map2Set;

/**
 * Learner for BLN variable domains.
 * @author Dominik Jain
 */
public class DomainLearner extends probcog.bayesnets.learning.DomainLearner {
	public DomainLearner(RelationalBeliefNetwork bn) {
		super(bn);
	}

	public void learn(GenericDatabase<?, ?> db) throws ProbCogException {
		// all domains are directly learned
		boolean debug = false;
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork) this.bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			ExtendedNode extNode = bn.getExtendedNode(i);
			boolean mustApplyBooleanDomain = false;
			// for decision nodes, set standard boolean domain
			if(extNode instanceof DecisionNode)
				mustApplyBooleanDomain = true;
			// for relational nodes...
			if(extNode instanceof RelationalNode) {
				RelationalNode node = (RelationalNode) extNode;
				// ...apply boolean domain if it's a built-in predicate or if
				// the sig is boolean
				mustApplyBooleanDomain = node.isBuiltInPred();
				if(!mustApplyBooleanDomain) {
					if(debug)
						System.out.println("node: " + node);
					String returnType = node.getReturnType();
					if(returnType == null) {
						throw new ProbCogException("Could not return type of " + node.getFunctionName());
					}
					if(BooleanDomain.isBooleanType(returnType))
						mustApplyBooleanDomain = true;
					else { // ... otherwise apply the values we have in the database
						Iterable<String> values = db.getDomain(returnType);
						if(values == null) {
							db.printDomain(System.out);
							throw new ProbCogException("Domain '" + returnType + "' of node '" + nodes[i].getName() + "' has no values in the database.");
						}
						for(String value : values) {
							if(debug)
								System.out.println("adding " + value + " to " + returnType + " while processing " + node.getFunctionName() + " - returnType = " + returnType);
							directDomainData.get(i).add(value);
						}
						continue;
					}
				}
			}
			if(mustApplyBooleanDomain) {
				directDomainData.get(i).add(BooleanDomain.True);
				directDomainData.get(i).add(BooleanDomain.False);
				continue;
			}
		}
	}

	protected void end_learning() throws ProbCogException {
		super.end_learning();

		// standardize boolean domains and write learnt domains to model
		Discrete booleanDomain = new Discrete(new String[] { BooleanDomain.True, BooleanDomain.False });
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork) this.bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		Map2Set<String, String> guaranteedElements = new Map2Set<String, String>();
		for(int i = 0; i < nodes.length; i++) {
			if (verbose) System.out.println("  " + nodes[i].getName());
			ExtendedNode extNode = bn.getExtendedNode(i);
			Discrete dom = (Discrete) nodes[i].getDomain();
			if(RelationalBeliefNetwork.isBooleanDomain(dom)) { // replace boolean domains
				if(extNode instanceof RelationalNode) {
					Signature sig = bn.getSignature((RelationalNode) extNode);
					if(sig != null)
						sig.returnType = BooleanDomain.typeName;
				}
				bn.bn.changeBeliefNodeDomain(nodes[i], booleanDomain);
			}
			else { // build up domain for relational belief network
				if(extNode instanceof RelationalNode) {
					String type = ((RelationalNode) extNode).getReturnType();
					for(int j = 0; j < dom.getOrder(); j++)
						guaranteedElements.add(type, dom.getName(j));					
				}
			}
		}
		// write guaranteed domains to rbn
		for(Entry<String, Set<String>> e : guaranteedElements.entrySet()) {
			Set<String> s = e.getValue();
			bn.setGuaranteedDomainElements(e.getKey(), s.toArray(new String[s.size()]));
		}
	}
}
