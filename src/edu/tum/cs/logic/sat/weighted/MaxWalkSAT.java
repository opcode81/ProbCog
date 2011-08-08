/*
 * Created on Jun 15, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.logic.sat.weighted;

import java.util.Vector;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.SampleSAT;
import edu.tum.cs.srl.Database;

/**
 * @author jain
 */
public class MaxWalkSAT extends SampleSAT implements IMaxSAT {
	protected int maxSteps = 1000;
	protected PossibleWorld bestState = null;

	public MaxWalkSAT(WeightedClausalKB kb, PossibleWorld state, WorldVariables vars, Database db) throws Exception {
		super(kb, state, vars, db.getEntries());
	}
	
	@Override
	protected Constraint makeConstraint(edu.tum.cs.logic.sat.Clause c) {
		return new WeightedClause((edu.tum.cs.logic.sat.weighted.WeightedClause)c);
	}
	
	protected class WeightedClause extends Clause {
		boolean isHard;
		double weight;

		public WeightedClause(edu.tum.cs.logic.sat.weighted.WeightedClause wc) {
			super(wc.lits);
			weight = wc.weight;
			isHard = wc.isHard;
		}
	}
	
	@Override
	protected int deltaCost(GroundAtom gndAtom) {
		int delta = 0;
		// consider newly unsatisfied constraints (negative)
		Vector<Constraint> bn = this.bottlenecks.get(gndAtom.index);
		if(bn != null)
			for(Constraint c : bn)
				delta -= ((WeightedClause)c).weight;
		// consider newly satisfied constraints (positive)
		Vector<Constraint> occs = this.GAOccurrences.get(gndAtom.index);
		if(occs != null)
			for(Constraint c : occs)
				if(c.flipSatisfies(gndAtom))
					delta += ((WeightedClause)c).weight; 
		return delta;
	}
	
	@Override
	public void makeMove() {
		walkSATMove();
	}

	@Override
	public PossibleWorld getBestState() {
		return bestState;
	}
	
	@Override
	public void run() throws Exception {		
		initialize();		
		
		double bestSum = Double.MAX_VALUE;
		int bestHardMissing = Integer.MAX_VALUE;
		for(int step = 1; step <= this.maxSteps; step++) {
			
			double unsatisfiedSum = 0.0;
			int hardMissing = 0;
			for(Constraint c : unsatisfiedConstraints) {
				WeightedClause wc = (WeightedClause)c;
				unsatisfiedSum += wc.weight;
				if(wc.isHard)
					hardMissing++;
			}
			
			boolean newBest = false;
			if(unsatisfiedSum < bestSum) {
				bestSum = unsatisfiedSum;
				bestHardMissing = hardMissing;
				newBest = true;
				this.bestState = state.clone();
			}
			
			boolean printStatus = newBest || step % 10 == 0;
			if(printStatus)
				System.out.printf("  step %d: %d hard constraints unsatisfied, sum of unsatisfied weights: %f, best: %f  %s\n", step, hardMissing, unsatisfiedSum, bestSum, newBest ? "[NEW BEST]" : "");
			
			if(unsatisfiedSum == 0)
				break;
			
			makeMove();
		}
		System.out.printf("solution quality: sum of unsatisfied constraints: %f, hard constraints unsatisfied: %d\n", bestSum, bestHardMissing);
	}

	@Override
	public void setMaxSteps(int steps) {
		maxSteps = steps;	
	}
	
	@Override
	public String getAlgorithmName() {
		return String.format("%s[p=%f]", this.getClass().getSimpleName(), this.pWalkSAT);
	}
}
