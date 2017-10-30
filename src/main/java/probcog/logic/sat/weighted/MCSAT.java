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

import java.util.Random;
import java.util.Vector;

import probcog.exception.ProbCogException;
import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.logic.Formula;
import probcog.logic.GroundAtom;
import probcog.logic.GroundLiteral;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.logic.sat.SampleSAT;
import probcog.logic.sat.weighted.WeightedClausalKB.FormulaAndClauses;
import probcog.srl.Database;


/**
 * Implementatoin of the MC-SAT inference algorithm (Poon and Domingos 2006).
 * Also includes extensions for soft evidence, MC-SAT-PC (Jain and Beetz 2010).
 * @author Dominik Jain
 */
public class MCSAT implements IParameterHandler {

	protected WeightedClausalKB kb;
	protected WorldVariables vars;
	protected Database db; 
	protected Random rand;
	protected GroundAtomDistribution dist;
	protected boolean verbose = true, debug = false;
	protected int infoInterval = 100;
	protected ParameterHandler paramHandler;
	protected SampleSAT sat;
	protected Vector<SoftEvidence> softEvidence;
	
	public class SoftEvidence {
		public WeightedClause wc;
		public double p;
		public double count;
		public SoftEvidence(WeightedClause wc, double p) {
			count = 0;
			this.wc = wc;
			this.p = p;
		}
	}
	
	public MCSAT(WeightedClausalKB kb, WorldVariables vars, Database db) throws ProbCogException {
		this.kb = kb;
		this.vars = vars;
		this.db = db;
		this.rand = new Random();
		this.dist = new GroundAtomDistribution(vars);
		this.paramHandler = new ParameterHandler(this);
		this.softEvidence = new Vector<SoftEvidence>();
		PossibleWorld state = new PossibleWorld(vars);
		sat = new SampleSAT(state, vars, db.getEntries());				
		paramHandler.addSubhandler(sat.getParameterHandler());
		paramHandler.add("infoInterval", "setInfoInterval");
		paramHandler.add("verbose", "setVerbose");
		paramHandler.add("random", "setRandom");
	}	
	
	public WeightedClausalKB getKB() {
		return kb;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public void setDebugMode(boolean active) {
		this.debug = active;
	}
	
	public void setInfoInterval(int interval) {
		this.infoInterval = interval;
	}
	
	public void setRandom(Random random) {
		this.rand = random;
	}

	public GroundAtomDistribution run(int steps) throws ProbCogException {
		if(debug) {
			System.out.println("\nMC-SAT constraints:");
			for(WeightedClause wc : kb)
				System.out.println("  " + wc);
			System.out.println();
		}
		verbose = verbose || debug;
		if(verbose) 
			System.out.printf("%s sampling (%d weighted formulas)...\n", this.getAlgorithmName(), this.kb.size());		
		
		// find initial state satisfying all hard constraints
		if(verbose) System.out.println("finding initial state...");
		Vector<WeightedClause> M = new Vector<WeightedClause>();
		for(FormulaAndClauses fac : kb.getFormulasAndClauses()) {
			WeightedFormula wf = fac.weightedFormula;
			if(wf.isHard) {
				M.addAll(fac.weightedClauses);
			}
		}		
		sat.setDebugMode(debug);
		sat.initConstraints(M);
		sat.run();
		
		// actual MC-SAT sampling
		for(int i = 0; i < steps; i++) {
			
			M.clear();
			
			for(FormulaAndClauses fac : kb.getFormulasAndClauses()) {
				WeightedFormula wf = fac.weightedFormula;
				if(wf.formula.isTrue(sat.getState())){
					boolean satisfy = wf.isHard || rand.nextDouble() * Math.exp(wf.weight) > 1.0;
					if(satisfy)
						M.addAll(fac.weightedClauses);					
				}				
			}
			
			// soft evidence clauses
			if(i > 0)
				for(SoftEvidence se : this.softEvidence) {
					if(se.wc.isTrue(sat.getState())) {
						se.count += 1;
						if(se.count/i < se.p)
							M.add(se.wc);							
					}
				}			
			
			if(verbose && (i+1) % infoInterval == 0) {
				System.out.printf("MC-SAT step %d: %d constraints to be satisfied\n", i+1, M.size());
				if(debug) {
					for(WeightedClause wc : M)
						System.out.println("    " + wc);
				}
			}
			sat.initConstraints(M);
			sat.run();
			
			synchronized(dist) {
				dist.addSample(sat.getState(), 1.0);
			}
		}
		synchronized(dist) {
			dist.normalize();
		}
		
		return dist;
	}
	
	public void setP(double p) {
		sat.setPSampleSAT(p);
	}
	
	public static class GroundAtomDistribution implements Cloneable {
		public double[] sums;
		public double Z;
		public int numSamples;
		
		public GroundAtomDistribution(WorldVariables vars){
			this.Z = 0.0;
			this.numSamples = 0;
			this.sums = new double[vars.size()];
		}
		
		public void addSample(PossibleWorld w, double weight){
			for(GroundAtom ga : w.getVariables()){
				if(w.isTrue(ga)){
					sums[ga.index] += weight;
				}
			}
			Z += weight;
			numSamples++;
		}
		
		public void normalize(){
			if(Z != 1.0) {
				for(int i = 0; i < sums.length; i++){
					sums[i] /= Z;
				}
				Z = 1.0;
			}
		}
		
		public double getResult(int indx){
			return sums[indx];
		}
		
		public GroundAtomDistribution clone() throws CloneNotSupportedException {
			return (GroundAtomDistribution)super.clone();
		}
	}

	public double getResult(GroundAtom ga) {
		return dist.getResult(ga.index);
	}
	
	public GroundAtomDistribution pollResults() throws ProbCogException {
		GroundAtomDistribution ret = null;
		synchronized(dist) {
			try {
				ret = this.dist.clone();
			} 
			catch (CloneNotSupportedException e) {
				throw new ProbCogException(e);
			}
		}
		return ret;
	}

	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	public String getAlgorithmName() {
		return String.format("%s[%s]", this.getClass().getSimpleName(), sat.getAlgorithmName());
	}
	
	public void addSoftEvidence(GroundAtom ga, double p) throws ProbCogException {
		Formula nga = new GroundLiteral(false, ga);		
		this.softEvidence.add(new SoftEvidence(new WeightedClause(ga, 0.0, false), p));
		this.softEvidence.add(new SoftEvidence(new WeightedClause(nga, 0.0, false), 1.0-p));
	}
}
