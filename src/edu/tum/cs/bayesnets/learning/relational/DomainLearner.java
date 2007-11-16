package edu.tum.cs.bayesnets.learning.relational;

import java.util.HashSet;
import java.util.Set;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.core.relational.RelationalNode;
import edu.tum.cs.bayesnets.core.relational.RelationalNode.Signature;

public class DomainLearner extends edu.tum.cs.bayesnets.learning.DomainLearner {
	public DomainLearner(RelationalBeliefNetwork bn) {
		super(bn);		
	}
	
	public void learn(Database db) throws Exception {
		// directly learned domains
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode node = bn.getRelationalNode(i);
			Signature sig = bn.getSignature(node.getName());
			Set<String> values = db.getDomain(sig.returnType);
			for(String value : values)
				((HashSet<String>)directDomainData[i]).add(value);
		}
	}
	
	protected void end_learning() throws Exception {
		super.end_learning();
		
		// standardize boolean domains
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			if(bn.isBooleanDomain((Discrete)nodes[i].getDomain())) {
				bn.getSignature(bn.getRelationalNode(i).getName()).returnType = "Boolean";
				bn.bn.changeBeliefNodeDomain(nodes[i], new Discrete(new String[]{"True", "False"}));
			}
		}
	}
}
