/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
package probcog.srl.directed.inference;

import java.util.Vector;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.inference.ITimeLimitedInference;
import probcog.bayesnets.inference.SampledDistribution;
import probcog.logic.Disjunction;
import probcog.logic.Formula;
import probcog.logic.GroundLiteral;
import probcog.logic.sat.weighted.WeightedClausalKB;
import probcog.logic.sat.weighted.WeightedClause;
import probcog.logic.sat.weighted.WeightedFormula;
import probcog.logic.sat.weighted.MCSAT.GroundAtomDistribution;
import probcog.srl.directed.bln.GroundBLN;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;

/**
 * MC-SAT inference for Bayesian logic networks 
 * @author Dominik Jain
 */
public class MCSAT extends Sampler implements ITimeLimitedInference {

	protected GroundBLN gbln;
	protected WeightedClausalKB kb;
	protected double maxWeight = 0;
	/**
	 * temporary collection of hard constraints appearing in the CPTs of the ground BN
	 */
	protected Vector<Disjunction> hardConstraintsInCPTs = new Vector<Disjunction>();
	protected probcog.logic.sat.weighted.MCSAT sampler;
	
	public MCSAT(GroundBLN gbln) throws Exception {
		super(gbln);
		this.gbln = gbln;		
	}
	
	@Override
	protected void _initialize() throws Exception {
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
		// construct sampler
		sampler = new probcog.logic.sat.weighted.MCSAT(kb, gbln.getWorldVars(), gbln.getDatabase());
		// pass on parameter handling
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
	public SampledDistribution _infer() throws Exception {
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
