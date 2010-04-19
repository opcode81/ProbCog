/*
 * Created on Aug 7, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.ITimeLimitedInference;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.logic.Disjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.sat.weighted.WeightedClausalKB;
import edu.tum.cs.logic.sat.weighted.WeightedClause;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.logic.sat.weighted.MCSAT.GroundAtomDistribution;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;

/**
 * MC-SAT inference for Bayesian logic networks 
 * @author jain
 */
public class MCSAT extends Sampler implements ITimeLimitedInference {

	protected GroundBLN gbln;
	protected WeightedClausalKB kb;
	protected double maxWeight = 0;
	/**
	 * temporary collection of hard constraints appearing in the CPTs of the ground BN
	 */
	protected Vector<Disjunction> hardConstraintsInCPTs = new Vector<Disjunction>();
	protected edu.tum.cs.logic.sat.weighted.MCSAT sampler;
	
	public MCSAT(GroundBLN gbln) throws Exception {
		super(gbln);
		this.gbln = gbln;
		kb = new WeightedClausalKB();		
		// add weighted clauses for probabilistic constraints
		for(BeliefNode n : gbln.getRegularVariables()) {
			CPF cpf = n.getCPF();
			BeliefNode[] domProd = cpf.getDomainProduct();
			walkCPT4ClauseCollection(cpf, domProd, new int[domProd.length], 0);						
		}
		// add weighted clauses for hard constraints
		double hardWeight = maxWeight + 100;
		for(Formula f : gbln.getKB()) {
			kb.addFormula(new WeightedFormula(f, hardWeight, true), false);
		}
		for(Disjunction f : hardConstraintsInCPTs) 
			kb.addClause(new WeightedClause(f, hardWeight, true));
		// clean up
		hardConstraintsInCPTs = null;
		sampler = new edu.tum.cs.logic.sat.weighted.MCSAT(kb, gbln.getWorldVars(), gbln.getDatabase());
		// parameter handling
		paramHandler.addSubhandler(sampler.getParameterHandler());
	}
	
	protected void walkCPT4ClauseCollection(CPF cpf, BeliefNode[] domProd, int[] domainIndices, int i) throws Exception {
		if(i == domainIndices.length) {
			// create disjunction of negated literals corresponding to domain index configuration
			GroundLiteral[] lits = new GroundLiteral[domainIndices.length];
			for(int j = 0; j < domainIndices.length; j++) {
				lits[j] = gbln.getGroundLiteral(domProd[j], domainIndices[j]);
				lits[j].negate();
			}
			Disjunction f = new Disjunction(lits);
			// obtain probability value and add to collection
			double p = cpf.getDouble(domainIndices);
			if(p == 0.0) { // this constraint is actually hard, so remember it for later
				hardConstraintsInCPTs.add(f);
			}
			else { // it is a soft constraint, whose negation we add to the KB
				double weight = -Math.log(p);
				kb.addClause(new WeightedClause(f, weight, false));
				if(weight > maxWeight)
					maxWeight = weight;
			}
			return;
		}	
		// recurse
		for(int j = 0; j < domProd[i].getDomain().getOrder(); j++) {
			domainIndices[i] = j;
			walkCPT4ClauseCollection(cpf, domProd, domainIndices, i+1);
		}
	}
	
	@Override
	public SampledDistribution infer() throws Exception {
		sampler.setDebugMode(this.debug);
		sampler.setVerbose(true);
		sampler.setInfoInterval(infoInterval);
		GroundAtomDistribution gad = sampler.run(numSamples);		
		return getSampledDistribution(gad);	
	}
	
	protected SampledDistribution getSampledDistribution(GroundAtomDistribution gad) throws Exception {
		gad.normalize();
		BeliefNetworkEx bn = gbln.getGroundNetwork();
		SampledDistribution dist = new SampledDistribution(bn);
		for(BeliefNode n : gbln.getRegularVariables()) {
			int idx = bn.getNodeIndex(n);
			for(int k = 0; k < n.getDomain().getOrder(); k++) {
				GroundLiteral lit = gbln.getGroundLiteral(n, k);
				dist.values[idx][k] = gad.getResult(lit.gndAtom.index);
				if(!lit.isPositive)
					dist.values[idx][k] = 1-dist.values[idx][k];
			}
		}
		for(BeliefNode n : gbln.getAuxiliaryVariables()) {
			int idx = bn.getNodeIndex(n);
			dist.values[idx][0] = 1.0;
			dist.values[idx][1] = 0.0;
		}
		dist.Z = 1.0;
		dist.trials = dist.steps = gad.numSamples;
		return dist;
	}

	public SampledDistribution pollResults() throws Exception {		
		return getSampledDistribution(sampler.pollResults());
	}
}
