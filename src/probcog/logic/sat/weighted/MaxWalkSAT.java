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
package probcog.logic.sat.weighted;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import probcog.logging.PrintLogger.Level;
import probcog.logic.GroundAtom;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.logic.sat.SampleSAT;
import probcog.srl.Database;


/**
 * Implementation of the MaxWalkSAT algorithm as described Kautz, Selman & Jiang (1997)
 * @author Dominik Jain
 */
public class MaxWalkSAT extends SampleSAT implements IMaxSAT {
	protected int maxSteps = 1000;
	protected PossibleWorld bestState = null;
	protected double sumOfWeightsOfUnsatisfiedConstraints;
	protected List<WeightedClause> unsatisfiedHardConstraints;

	public MaxWalkSAT(WeightedClausalKB kb, PossibleWorld state, WorldVariables vars, Database db) throws Exception {
		super(kb, state, vars, db.getEntries());
	}
	
	@Override
	protected Constraint makeConstraint(probcog.logic.sat.Clause c) {
		return new WeightedClause((probcog.logic.sat.weighted.WeightedClause)c);
	}
	
	protected class WeightedClause extends Clause {
		boolean isHard;
		double weight;

		public WeightedClause(probcog.logic.sat.weighted.WeightedClause wc) {
			super(wc.lits);
			weight = wc.weight;
			isHard = wc.isHard;
		}
	}
	
	@Override
	protected double deltaCost(GroundAtom gndAtom) {
		double delta = 0;
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
	protected void walkSATMove() {
		// with probability p, make a random move (satisfy any unsatisfied constraint randomly)		
		if(rand.nextDouble() < this.pWalkSAT) {
			Constraint c = unsatisfiedConstraints.get(rand.nextInt(unsatisfiedConstraints.size()));
			c.satisfyRandomly();
		}			 
		// otherwise, make a greedy move
		else {
			// if there are unsatisfied hard constraints, satisfy one of them greedily
			if(!unsatisfiedHardConstraints.isEmpty()) {
				Constraint c = unsatisfiedHardConstraints.get(rand.nextInt(unsatisfiedHardConstraints.size()));
				c.satisfyGreedily();
			}
			// otherwise, pick any unsatisfied constraint
			else {
				Constraint c = unsatisfiedConstraints.get(rand.nextInt(unsatisfiedConstraints.size()));
				c.satisfyGreedily();
			}
		}
	}

	@Override
	public PossibleWorld getBestState() {
		return bestState;
	}
	
	@Override
	protected void addUnsatisfiedConstraint(Constraint c) {
		super.addUnsatisfiedConstraint(c);
		
		WeightedClause wc = (WeightedClause)c;
		sumOfWeightsOfUnsatisfiedConstraints += wc.weight; 
		
		if (wc.isHard)
			unsatisfiedHardConstraints.add(wc);
	}
	
	@Override
	protected void removeUnsatisfiedConstraint(Constraint c) {
		super.removeUnsatisfiedConstraint(c);
		
		WeightedClause wc = (WeightedClause)c;
		sumOfWeightsOfUnsatisfiedConstraints -= wc.weight; 
		
		if (wc.isHard)
			unsatisfiedHardConstraints.remove(wc);
	}
	
	@Override
	protected void initialize() throws Exception {
		sumOfWeightsOfUnsatisfiedConstraints = 0;
		unsatisfiedHardConstraints = new ArrayList<>();
		
		super.initialize();
	}
	
	@Override
	public void run() throws Exception {
		initialize();	
		
		double bestSum = Double.MAX_VALUE;
		int bestHardMissing = Integer.MAX_VALUE;
		for(int step = 1; step <= this.maxSteps; step++) {
			
			int hardMissing = unsatisfiedHardConstraints.size();
			boolean newBest = false;
			if(sumOfWeightsOfUnsatisfiedConstraints < bestSum) {
				bestSum = sumOfWeightsOfUnsatisfiedConstraints;
				bestHardMissing = hardMissing;
				newBest = true;
				this.bestState = state.clone();
			}

			boolean printStatus = newBest || step % 10 == 0;
			if(printStatus) {
				log.out(Level.INFO, newBest ? Level.DEBUG : Level.TRACE, 
						String.format("  step %d: %d hard constraints unsatisfied, sum of unsatisfied weights: %f, " + 
								"best: %f (%d) %s", 
								step, hardMissing, sumOfWeightsOfUnsatisfiedConstraints, 
								bestSum, bestHardMissing, 
								newBest ? "[NEW BEST]" : ""));
			}
			
			if(sumOfWeightsOfUnsatisfiedConstraints == 0)
				break;
			
			makeMove();
		}
		log.info(String.format("solution quality: sum of unsatisfied constraints: %f, hard constraints unsatisfied: %d", bestSum, bestHardMissing));

		// log unsatisfied hard constraints
		if (log.isDebugEnabled()) {
			PossibleWorld bestState = this.getBestState();
			for(Constraint c : this.constraints) {
				WeightedClause wc = (WeightedClause)c;
				if(wc.isHard) {
					if(!wc.isTrue(bestState))
						log.debug(wc.toString());
				}
			}
		}
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
