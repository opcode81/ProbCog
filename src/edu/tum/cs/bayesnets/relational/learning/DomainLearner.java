package edu.tum.cs.bayesnets.relational.learning;

import java.util.HashSet;
import java.util.Set;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.relational.core.RelationalNode;
import edu.tum.cs.bayesnets.relational.core.RelationalNode.Signature;

public class DomainLearner extends edu.tum.cs.bayesnets.learning.DomainLearner {
	public DomainLearner(RelationalBeliefNetwork bn) {
		super(bn);		
	}
	
	public void learn(Database db) throws Exception {
		// directly learned domains
		boolean debug = false;
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode node = bn.getRelationalNode(i);
			// for built-in predicates, set standard boolean domain
			if(node.isBuiltInPred()) {
				((HashSet<String>)directDomainData[i]).add("True");
				((HashSet<String>)directDomainData[i]).add("False");
				continue;
			}
			// for regular nodes, get all values from the database
			if(debug) System.out.println("node: " + node);
			Signature sig = bn.getSignature(node.getFunctionName());
			if(sig == null) {
				throw new Exception("Could not obtain signature of " + node.getFunctionName());
			}			
			Set<String> values = db.getDomain(sig.returnType);
			for(String value : values) {
				if(debug) System.out.println("adding " + value + " to " + sig.returnType);
				((HashSet<String>)directDomainData[i]).add(value);
			}
		}
	}
	
	protected void end_learning() throws Exception {
		super.end_learning();
		
		// standardize boolean domains
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			System.out.println(nodes[i].getName());
			if(bn.isBooleanDomain((Discrete)nodes[i].getDomain())) {
				Signature sig = bn.getSignature(bn.getRelationalNode(i));
				if(sig != null)
					sig.returnType = "Boolean";
				bn.bn.changeBeliefNodeDomain(nodes[i], new Discrete(new String[]{"True", "False"}));
			}
		}
	}
}
