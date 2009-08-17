package edu.tum.cs.srl.bayesnets.inference;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.logic.Disjunction;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.sat.ClausalKB;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;

/**
 * extended version of the SAT-IS algorithm, where the knowledge base is augmented with formulas 
 * based on 0 entries in probabilistic constraints, which factually represent deterministic 
 * constraints 
 * @author jain
 */
public class SATISEx extends SATIS {

	public SATISEx(GroundBLN bln) throws Exception {
		super(bln);
	}
	
	@Override
	protected ClausalKB getClausalKB() throws Exception {
		ClausalKB ckb = super.getClausalKB();
		
		// extend the KB with formulas based on a CPD analysis
		int size = ckb.size();
		System.out.print("extending KB with deterministic constraints from CPDs... ");
		BeliefNetworkEx bn = gbln.getGroundNetwork();
		for(BeliefNode node : bn.bn.getNodes()) {
			if(!gbln.isRegularVariable(node))
				continue;
			CPF cpf = node.getCPF();
			BeliefNode[] domProd = cpf.getDomainProduct();
			int[] addr = new int[domProd.length];
			walkCPF4HardConstraints(cpf, addr, 0, ckb);
		}
		System.out.println((ckb.size()-size) + " constraints added");
		
		return ckb;
	}

	protected void walkCPF4HardConstraints(CPF cpf, int[] addr, int i, ClausalKB ckb) throws Exception {
		BeliefNode[] domProd = cpf.getDomainProduct();
		if(i == addr.length) {
			double p = cpf.getDouble(addr);
			if(p == 0.0) {
				GroundLiteral[] lits = new GroundLiteral[domProd.length]; 
				for(int k = 0; k < domProd.length; k++) {
					lits[k] = gbln.getGroundLiteral(domProd[k], addr[k]);
					lits[k].negate();
				}
				ckb.addFormula(new Disjunction(lits));
			}
			return;
		}
		for(int k = 0; k < domProd[i].getDomain().getOrder(); k++) {
			addr[i] = k;
			walkCPF4HardConstraints(cpf, addr, i+1, ckb);
		}
	}
}
