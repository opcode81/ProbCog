package edu.tum.cs.logic.sat;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Vector;
import java.util.Map.Entry;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.srl.bayesnets.ABL;
import edu.tum.cs.srl.bayesnets.Database;
import edu.tum.cs.srl.bayesnets.Database.Variable;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.tools.Stopwatch;
import edu.tum.cs.tools.StringTool;

/**
 * Implementation of the stochastic SAT sampling algorithm SampleSAT by Wei et al.
 * It near-uniformly samples a solution from the set of solutions
 * 
 * @author jain
 */
public class SampleSAT {
	protected HashMap<Integer,Vector<Constraint>> bottlenecks;
	protected HashMap<Integer,Vector<Constraint>> GAOccurrences;
	protected PossibleWorld state;
	protected Vector<Constraint> unsatisfiedConstraints;
	protected Vector<Constraint> constraints;
	protected Random rand;
	protected WorldVariables vars;
	protected HashMap<Integer,Boolean> evidence;
	protected final boolean verbose = false;
	
	public SampleSAT(ClausalKB kb, PossibleWorld state, WorldVariables vars, Database evidence) throws Exception {
		System.out.println("KB:");
		kb.print();
		
		this.state = state;
		this.vars = vars;
		this.unsatisfiedConstraints = new Vector<Constraint>();
		bottlenecks = new HashMap<Integer,Vector<Constraint>>();
		GAOccurrences = new HashMap<Integer,Vector<Constraint>>();
		rand = new Random();
		constraints = new Vector<Constraint>(kb.size());
		
		// read evidence
		System.out.println("evidence:");
		evidence.print();
		this.evidence = new HashMap<Integer,Boolean>();
		for(Variable var : evidence.getEntries()) {
			String strGndAtom = var.getPredicate(evidence.rbn);
			GroundAtom gndAtom = vars.get(strGndAtom);			
			Block block = vars.getBlock(gndAtom.index);
			if(block != null) 
				for(GroundAtom ga : block)
					this.evidence.put(ga.index, var.value.equals(ga.args[ga.args.length-1]));				
			else 
				this.evidence.put(gndAtom.index, var.isTrue());			
		}
		
		// set evidence
		for(Entry<Integer, Boolean> e : this.evidence.entrySet()) 
			state.set(e.getKey(), e.getValue());
		
		// TODO since formulas never change it could make sense to remove all evidence atoms from all formulas
		// TODO it might make sense to add additional hard constraints for all 0 entries in all cpts that forbid the parent-child configuration
		// TODO unit propagation
		
		// instantiate constraints
		for(edu.tum.cs.logic.sat.Clause c : kb) 
			constraints.add(new Clause(c.lits));		
	}
	
	protected void addUnsatisfiedConstraint(Constraint c) {
		unsatisfiedConstraints.add(c);
	}
	
	protected void addBottleneck(GroundAtom a, Constraint c) {
		Vector<Constraint> v = bottlenecks.get(a.index);
		if(v == null) {
			v = new Vector<Constraint>();
			bottlenecks.put(a.index, v);
		}
		v.add(c);
	}
	
	protected void addGAOccurrence(GroundAtom a, Constraint c) {
		Vector<Constraint> v = GAOccurrences.get(a.index);
		if(v == null) {
			v = new Vector<Constraint>();
			GAOccurrences.put(a.index, v);
		}
		v.add(c);
	}
	
	public void run() {	
		// init
		bottlenecks.clear();
		unsatisfiedConstraints.clear();
		if(verbose) System.out.println("setting random state...");
		setRandomState();
		if(verbose) state.print();
		for(Constraint c : constraints)
			c.initState();
		
		if(verbose) System.out.println("running SampleSAT...");
		int step = 1;
		while(unsatisfiedConstraints.size() > 0) {			
			if(verbose /*|| step % 10 == 0*/) {				
				System.out.println("SampleSAT step " + step + ", " + unsatisfiedConstraints.size() + " constraints unsatisfied");
				if(false) {
					//state.print();				
					for(Constraint c : unsatisfiedConstraints) {
						System.out.println("  unsatisfied: " + c);
					}
				}
			}
			if(rand.nextDouble() < 0.9) {
				if(verbose) System.out.println("  Greedy Move:");
				walkSATMove();
			}
			else {
				if(verbose) System.out.println("  SAMove:");
				SAMove();
			}
			step++;
		}
		/*System.out.println("SampleSAT finished");
		System.exit(0);*/
	}
	
	public PossibleWorld getState() {
		return state;
	}
	
	protected void setRandomState() {
		for(int i = 0; i < vars.size();) {
			//System.out.println("  setting " + vars.get(i));
			Block block = vars.getBlock(i); 
			if(block != null) {
				if(!this.evidence.containsKey(i)) {
					int j = rand.nextInt(block.size());
					for(int k = 0; k < block.size(); k++) {
						boolean value = k == j; 
						state.set(i+k, value);
					}					
				}
				i += block.size();
			}
			else { 
				if(!this.evidence.containsKey(i))
					state.set(i, rand.nextBoolean());
				++i;
			}
		}		
	}
	
	protected void walkSATMove() {
		Constraint c = unsatisfiedConstraints.get(rand.nextInt(unsatisfiedConstraints.size()));
		c.greedySatisfy();
	}
	
	protected void SAMove() {
		boolean done = false;
		while(!done) {
			// randomly pick a ground atom to flip
			int idxGA = rand.nextInt(vars.size());
			GroundAtom gndAtom = vars.get(idxGA), gndAtom2 = null;
			// if it has evidence, skip it
			if(evidence.containsKey(idxGA))
				continue;
			// if it's in a block, must choose a second to flip
			Block block = vars.getBlock(idxGA);
			if(block != null) {				
				GroundAtom trueOne = block.getTrueOne(state);
				if(gndAtom == trueOne) { // if we are flipping the true one, pick the second at random among the others
					Vector<GroundAtom> others = new Vector<GroundAtom>();
					for(GroundAtom ga : block) {
						if(ga != trueOne && !evidence.containsKey(ga.index))
							others.add(ga);
					}
					if(others.isEmpty())
						continue;
					gndAtom2 = others.get(rand.nextInt(others.size()));
				}
				else { // second to flip must be true one
					if(evidence.containsKey(trueOne.index))
						continue;
					gndAtom2 = trueOne;
				}
			}
			// flip
			flipGndAtom(gndAtom);
			if(gndAtom2 != null)
				flipGndAtom(gndAtom2);
			done = true;
		}
	}
	
	protected void pickAndFlipVar(Collection<GroundAtom> candidates) {
		// find the best candidate
		GroundAtom bestGA = null, bestGASecond = null;
		int bestDelta = Integer.MIN_VALUE;
		for(GroundAtom gndAtom : candidates) {
			// if we have evidence, skip this ground atom
			if(evidence.containsKey(gndAtom.index))
				continue;
			// calculate delta-cost
			int delta = deltaCost(gndAtom);
			// - if the atom is in a block, we must consider the cost of flipping the second atom
			Block block = vars.getBlock(gndAtom.index);
			GroundAtom secondGA = null;
			if(block != null) {
				GroundAtom trueOne = block.getTrueOne(state);
				int delta2 = Integer.MIN_VALUE;
				if(gndAtom != trueOne) { // the second one to flip must be the true one
					secondGA = trueOne;
					delta2 = deltaCost(secondGA);
				}
				else { // as the second flip any one of the others (that has no evidence)
					for(GroundAtom ga2 : block) {
						if(evidence.containsKey(ga2.index) || ga2 == gndAtom)
							continue;
						int d = deltaCost(ga2);
						if(d > delta2) {
							delta2 = d;
							secondGA = ga2;
						}
					}
				}
				if(secondGA == null)
					continue;
				delta += delta2; // TODO additivity ignores possibility of first and second GA appearing in same formula (make temporary change!)
			}
			// is it better?
			boolean newBest = false;			
			if(delta > bestDelta) 
				newBest = true;			
			else if(delta == bestDelta && rand.nextInt(2) == 1)
				newBest = true;
			if(newBest) {
				bestGA = gndAtom;
				bestGASecond = secondGA;
				bestDelta = delta;
			}
		}
		// perform the flip
		flipGndAtom(bestGA);
		if(bestGASecond != null)
			flipGndAtom(bestGASecond);
	}
	
	protected void flipGndAtom(GroundAtom gndAtom) {
		if(verbose) System.out.println("  flipping " + gndAtom);
		// modify state
		boolean value = state.isTrue(gndAtom);
		state.set(gndAtom, !value);
		// the constraints where the literal was a bottleneck are now unsatisfied
		Vector<Constraint> bn = this.bottlenecks.get(gndAtom.index);
		if(bn != null) {
			this.unsatisfiedConstraints.addAll(bn);
			bn.clear();
		}
		// other stuff is handled by the constraints themselves
		Vector<Constraint> occ = this.GAOccurrences.get(gndAtom.index);
		if(occ != null)
			for(Constraint c : occ)
				c.handleFlip(gndAtom);
	}
	
	protected int deltaCost(GroundAtom gndAtom) {
		int delta = 0;
		// consider newly unsatisfied constraints (negative)
		Vector<Constraint> bn = this.bottlenecks.get(gndAtom.index);
		if(bn != null) 
			delta -= bn.size();
		// consider newly satisfied constraints (positive)
		for(Constraint c : this.GAOccurrences.get(gndAtom.index))
			if(c.flipSatisfies(gndAtom))
				delta++; 
		return delta;
	}
	
	protected abstract class Constraint {
		public abstract void greedySatisfy();
		public abstract boolean flipSatisfies(GroundAtom gndAtom);
		public abstract void handleFlip(GroundAtom gndAtom);
		public abstract void initState();
	}
	
	protected class Clause extends Constraint {
		protected GroundLiteral[] lits;
		protected Vector<GroundAtom> gndAtoms;
		protected HashSet<GroundAtom> trueOnes;
		
		public Clause(GroundLiteral[] lits) {	
			this.lits = lits;
			// collect ground atom occurrences 
			gndAtoms = new Vector<GroundAtom>();
			trueOnes = new HashSet<GroundAtom>();
			for(GroundLiteral lit : lits) {
				GroundAtom gndAtom = lit.gndAtom;
				gndAtoms.add(gndAtom);
				addGAOccurrence(gndAtom, this);
			}
		}

		@Override
		public void greedySatisfy() {
			pickAndFlipVar(gndAtoms);
		}

		@Override
		public boolean flipSatisfies(GroundAtom gndAtom) {
			return trueOnes.size() == 0;
		}

		@Override
		public void handleFlip(GroundAtom gndAtom) {
            int numTrueLits = trueOnes.size();
            if(trueOnes.contains(gndAtom)) { // the lit was true and is now false, remove it from the clause's list of true lits
                trueOnes.remove(gndAtom);
                numTrueLits--;
                // if no more true lits are left, the clause is now unsatisfied; this is handled in flipGndAtom
			}
            else { // the lit was false and is now true, add it to the clause's list of true lits
                if(numTrueLits == 0) // the clause was previously unsatisfied, it is now satisfied
                    unsatisfiedConstraints.remove(this);
                else if(numTrueLits == 1) // we are adding a second true lit, so the first one is no longer a bottleneck of this clause
                    bottlenecks.get(trueOnes.iterator().next().index).remove(this);
                trueOnes.add(gndAtom);
                numTrueLits++;
            }
            if(numTrueLits == 1)
                addBottleneck(trueOnes.iterator().next(), this);
		}
		
		@Override
		public String toString() {
			return StringTool.join(" v ", lits);
		}

		@Override
		public void initState() {
			trueOnes.clear();
			// find out which lits are true
			for(GroundLiteral lit : lits)
				if(lit.isTrue(state))
					trueOnes.add(lit.gndAtom);
			// if there are no true ones, this constraint is unsatisfied
			if(trueOnes.size() == 0)
				addUnsatisfiedConstraint(this);
			// if there is exactly one true literal, it is a bottleneck
			else if(trueOnes.size() == 1) 
				addBottleneck(trueOnes.iterator().next(), this);			
		}
	}	
	
	public static void main(String[] args) throws Exception {
		/*
		String blog = "relxy.blog";
		String net = "relxy.xml";
		String blnfile = "relxy.bln";
		String dbfile = "relxy.blogdb";
		*/
		String blog = "meals_any_for.blog";
		String net = "meals_any_for_functional.xml";
		String blnfile = "meals_any_for_functional.bln";
		String dbfile = "lorenzExample.blogdb";
		BayesianLogicNetwork bln = new BayesianLogicNetwork(new ABL(blog, net), blnfile);
		// read evidence
		Database db = new Database(bln.rbn);
		db.readBLOGDB(dbfile);
		// ground model
		GroundBLN gbln = new GroundBLN(bln, db);
		gbln.instantiateGroundNetwork();
		// run algorithm
		PossibleWorld state = new PossibleWorld(gbln.getWorldVars());
		ClausalKB ckb = new ClausalKB(gbln.getKB());
		Stopwatch sw = new Stopwatch();
		sw.start();
		SampleSAT ss = new SampleSAT(ckb, state, gbln.getWorldVars(), gbln.getDatabase());
		ss.run();
		sw.stop();
		/*System.out.println("SECOND RUN");
		ss.run();*/
		System.out.println("done");
		state.print();
		System.out.println("time taken: " + sw.getElapsedTimeSecs());		
	}
}
