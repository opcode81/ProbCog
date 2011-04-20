package edu.tum.cs.srl.bayesnets.learning;

import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.srl.BooleanDomain;
import edu.tum.cs.srl.GenericDatabase;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.DecisionNode;
import edu.tum.cs.srl.bayesnets.ExtendedNode;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.util.datastruct.Map2Set;

public class DomainLearner extends edu.tum.cs.bayesnets.learning.DomainLearner {
	public DomainLearner(RelationalBeliefNetwork bn) {
		super(bn);
	}

	public void learn(GenericDatabase<?, ?> db) throws Exception {
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
						throw new Exception("Could not return type of " + node.getFunctionName());
					}
					if(BooleanDomain.isBooleanType(returnType))
						mustApplyBooleanDomain = true;
					else { // ... otherwise apply the values we have in the database
						Iterable<String> values = db.getDomain(returnType);
						if(values == null) {
							db.printDomain(System.out);
							throw new Exception("Domain '" + returnType + "' of node '" + nodes[i].getName() + "' has no values in the database.");
						}
						for(String value : values) {
							if(debug)
								System.out.println("adding " + value + " to " + returnType + " while processing " + node.getFunctionName() + " - returnType = " + returnType);
							((HashSet<String>) directDomainData[i]).add(value);
						}
						continue;
					}
				}
			}
			if(mustApplyBooleanDomain) {
				((HashSet<String>) directDomainData[i]).add(BooleanDomain.True);
				((HashSet<String>) directDomainData[i]).add(BooleanDomain.False);
				continue;
			}
		}
	}

	protected void end_learning() throws Exception {
		super.end_learning();

		// standardize boolean domains and write learnt domains to model
		Discrete booleanDomain = new Discrete(new String[] { BooleanDomain.True, BooleanDomain.False });
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork) this.bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		Map2Set<String, String> guaranteedElements = new Map2Set<String, String>();
		for(int i = 0; i < nodes.length; i++) {
			System.out.println("  " + nodes[i].getName());
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
