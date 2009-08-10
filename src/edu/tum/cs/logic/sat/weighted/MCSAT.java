/*
 * Created on Aug 7, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.logic.sat.weighted;

import java.util.Random;
import java.util.Vector;
import java.util.Map.Entry;

import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.SampleSAT;
import edu.tum.cs.srl.Database;

public class MCSAT {

	protected WeightedClausalKB kb;
	protected WorldVariables vars;
	protected Database db; 
	protected Random rand;
	
	public MCSAT(WeightedClausalKB kb, WorldVariables vars, Database db) {		
		this.kb = kb;
		this.vars = vars;
		this.db = db;
		this.rand = new Random();
	}
	
	public void run(int steps) throws Exception {		
		// find initial state satisfying all hard constraints
		Vector<WeightedClause> M = new Vector<WeightedClause>();
		for(Entry<WeightedFormula, Vector<WeightedClause>> e : kb.getFormulasAndClauses()) {
			WeightedFormula wf = e.getKey();
			if(wf.isHard) {
				M.addAll(e.getValue());
			}
		}
		PossibleWorld state = new PossibleWorld(vars);
		SampleSAT sat = new SampleSAT(M, state, vars, db);
		sat.run();
		
		// actual MC-SAT sampling
		for(int i = 0; i < steps; i++) {
			M.clear();
			for(Entry<WeightedFormula, Vector<WeightedClause>> e : kb.getFormulasAndClauses()) {
				WeightedFormula wf = e.getKey();
				boolean satisfy = wf.isHard || rand.nextDouble() * Math.exp(wf.weight) > 1.0;
				if(satisfy)
					M.addAll(e.getValue());
			}
			sat.initConstraints(M);
			sat.run();
			
			// count sample
		}
	}
	
}
