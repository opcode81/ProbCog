package edu.tum.cs.bayesnets.inference;

import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.StringTool;

/**
 * The variable elimination algorithm for exact inference in Bayesian networks (see, e.g., AIMA ch. 14)
 * @author jain
 */
public class VariableElimination extends Sampler {
	int[] nodeOrder;
	Stopwatch timer;
	int[] nodeDomainIndices;
	
	public VariableElimination(BeliefNetworkEx bn) throws Exception {
		super(bn);
		nodeOrder = bn.getTopologicalOrder();
	}

	protected class Factor {
		CPF cpf;
		
		public Factor(BeliefNode n) {
			cpf = n.getCPF();
			BeliefNode[] domprod = cpf.getDomainProduct();
			for(int i = 0; i < domprod.length; i++) {
				if(evidenceDomainIndices[getNodeIndex(domprod[i])] != 0) {
					cpf = removeEvidence(cpf);
					break;	
				}
			}
		}
		
		protected CPF removeEvidence(CPF cpf) {
			BeliefNode[] domprod = cpf.getDomainProduct();
			Vector<BeliefNode> domprod2 = new Vector<BeliefNode>();
			for(int i = 0; i < domprod.length; i++)
				if(evidenceDomainIndices[getNodeIndex(domprod[i])] == -1)
					domprod2.add(domprod[i]);
			CPF cpf2 = new CPF(domprod2.toArray(new BeliefNode[domprod2.size()]));
			int[] addr = new int[domprod.length];
			int[] addr2 = new int[domprod2.size()];
			removeEvidence(cpf, cpf2, 0, addr, 0, addr2);
			return cpf2;
		}
		
		protected void removeEvidence(CPF cpf, CPF cpf2, int i, int[] addr, int j, int[] addr2) {
			if(i == addr.length) {
				cpf2.put(addr2, cpf.get(addr));
				return;
			}
			
			BeliefNode[] domprod = cpf.getDomainProduct();
			BeliefNode[] domprod2 = cpf2.getDomainProduct();			
			BeliefNode node = domprod[i];
			boolean transfer = false;
			if(j < domprod2.length) 
				transfer = domprod2[j] == domprod[i];
			int evidence = evidenceDomainIndices[getNodeIndex(node)];
			if(evidence != -1) { // this should never happen
				addr[i] = evidence;
				if(transfer)
					addr2[j] = evidence; 
				removeEvidence(cpf, cpf2, i+1, addr, transfer ? j+1 : j, addr2);
			}
			else {
				int domSize = node.getDomain().getOrder();
				for(int domIdx = 0; domIdx < domSize; domIdx++) {
					addr[i] = domIdx;
					if(transfer)
						addr2[j] = domIdx; 
					removeEvidence(cpf, cpf2, i+1, addr, transfer ? j+1 : j, addr2);
				}
			}
		}
		
		public Factor(CPF cpf) {
			this.cpf = cpf;
		}
		
		public double getValue(int[] nodeDomainIndices) {
			BeliefNode[] domProd = cpf.getDomainProduct();
			int[] addr = new int[domProd.length];
			for(int i = 0; i < addr.length; i++)
				addr[i] = nodeDomainIndices[getNodeIndex(domProd[i])];
			return cpf.getDouble(addr);
		}
		
		public Factor sumOut(BeliefNode n) {			
			BeliefNode[] domprod = cpf.getDomainProduct();
			BeliefNode[] domprod2 = new BeliefNode[domprod.length-1];
			int j = 0;
			for(int i = 0; i < domprod.length; i++)
				if(domprod[i] != n)
					domprod2[j++] = domprod[i];
			CPF cpf2 = new CPF(domprod2);
			int[] addr = new int[domprod.length];
			int[] addr2 = new int[domprod2.length];
			sumOut(cpf2, n, 0, addr, 0, addr2);
			return new Factor(cpf2);
		}
		
		protected void sumOut(CPF cpf2, BeliefNode n, int i, int[] addr, int j, int[] addr2) {
			if(i == addr.length) {
				int realaddr2 = cpf2.addr2realaddr(addr2);
				double v = cpf2.getDouble(realaddr2);
				v += cpf.getDouble(addr);
				cpf2.put(realaddr2, new ValueDouble(v));
				return;
			}
						
			BeliefNode node = this.cpf.getDomainProduct()[i];
			int evidence = evidenceDomainIndices[getNodeIndex(node)];
			if(evidence != -1) { // this should never happen
				addr[i] = evidence;
				if(node != n)
					addr2[j] = evidence; 
				sumOut(cpf2, n, i+1, addr, node == n ? j : j+1, addr2);
			}
			else {
				int domSize = node.getDomain().getOrder();
				for(int domIdx = 0; domIdx < domSize; domIdx++) {
					addr[i] = domIdx;
					if(node != n)
						addr2[j] = domIdx; 
					sumOut(cpf2, n, i+1, addr, node == n ? j : j+1, addr2);
				}
			}
		}
		
		public String toString() {
			return "F(" + StringTool.join(",", cpf.getDomainProduct()) + ")";
		}
	}

	protected Factor join(Iterable<Factor> factors) {	
		HashSet<BeliefNode> domain = new HashSet<BeliefNode>();
		for(Factor f : factors) {
			for(BeliefNode n : f.cpf.getDomainProduct())
				domain.add(n);
		}
		BeliefNode[] domProd = domain.toArray(new BeliefNode[domain.size()]);
		CPF cpf = new CPF(domProd);
		int[] addr = new int[domProd.length];
		fillCPF(factors, cpf, 0, addr);
		return new Factor(cpf);
	}
	
	protected void fillCPF(Iterable<Factor> factors, CPF cpf, int i, int[] addr) {
		if(i == addr.length) {
			double value = 1.0;
			for(Factor f : factors) {
				value *= f.getValue(nodeDomainIndices);
			}
			try {
				cpf.put(addr, new ValueDouble(value));
			}
			catch(Exception e) {
				System.err.println(StringTool.join(", ", cpf.getDomainProduct()));
				throw new RuntimeException(e);
			}
			return;
		}
		BeliefNode[] domProd = cpf.getDomainProduct(); 
		int domSize = domProd[i].getDomain().getOrder();
		for(int j = 0; j < domSize; j++) {
			addr[i] = j;
			nodeDomainIndices[getNodeIndex(domProd[i])] = j;
			fillCPF(factors, cpf, i+1, addr);
		}
	}
	
	protected Vector<Factor> sumout(Vector<Factor> factors, BeliefNode n) {
		Vector<Factor> newFacs = new Vector<Factor>();
		Vector<Factor> joinFacs = new Vector<Factor>();
		for(Factor f : factors) {
			BeliefNode[] domProd = f.cpf.getDomainProduct();
			boolean sumover = false;
			for(int i = 0; i < domProd.length; i++)
				if(domProd[i] == n)
					sumover = true;
			if(sumover)
				joinFacs.add(f);
			else
				newFacs.add(f);
		}
		Factor joinedFac = join(joinFacs);
		if(debug) out.println("Summing out " + n + " from " + joinedFac);
		newFacs.add(joinedFac.sumOut(n));
		return newFacs;
	}
	
	protected void computeMarginal(BeliefNode Q) {
		Vector<Factor> factors = new Vector<Factor>();
		for(int i = nodeOrder.length-1; i >= 0; i--) {
			if(!debug) out.printf("  %s  %d \r", Q.getName(), i);
			int nodeIdx = nodeOrder[i];
			BeliefNode node = nodes[nodeIdx];
			if(debug) out.println("Current node: " + node);			
			
			factors.add(new Factor(node));
			if(debug) out.println(factors);
			
			if(evidenceDomainIndices[nodeIdx] == -1 && node != Q)				
				factors = sumout(factors, node);			
		}
		if(!debug) out.println();
		
		if(debug) out.printf("%d final factors: %s\n", factors.size(), StringTool.join(", ", factors));
		
		// save results to distribution
		int nodeIdx = getNodeIndex(Q);
		double[] marginal = new double[Q.getDomain().getOrder()];
		double Z = 0.0;
		for(int i = 0; i < marginal.length; i++) {
			nodeDomainIndices[nodeIdx] = i;
			marginal[i] = 1.0;
			for(Factor f : factors) {			
				marginal[i] *= f.getValue(nodeDomainIndices);
			}
			//out.println(factors.get(0).getValue(nodeDomainIndices));
			Z += marginal[i];
		}
		for(int i = 0; i < marginal.length; i++)
			marginal[i] /= Z;
		dist.values[nodeIdx] = marginal;
	}
	
	public SampledDistribution _infer() throws Exception {
		Stopwatch sw = new Stopwatch();
		
		createDistribution();
		dist.Z = 1.0;
		
		sw.start();
		
		nodeDomainIndices = evidenceDomainIndices.clone();
		for(Integer nodeIdx : queryVars)
			computeMarginal(nodes[nodeIdx]);
		
		sw.stop();
		return dist;
	}
}
