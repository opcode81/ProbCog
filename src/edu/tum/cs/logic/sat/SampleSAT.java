package edu.tum.cs.logic.sat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.srl.AbstractVariable;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.ABLModel;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.StringTool;

/**
 * Implementation of the stochastic SAT sampling algorithm SampleSAT by Wei et al.
 * It near-uniformly samples a solution from the set of solutions
 * 
 * @author jain
 */
public class SampleSAT implements IParameterHandler {
	protected HashMap<Integer,Vector<Constraint>> bottlenecks;
	protected HashMap<Integer,Vector<Constraint>> GAOccurrences;
	protected PossibleWorld state;
	protected Vector<Constraint> unsatisfiedConstraints;
	protected Vector<Constraint> constraints;
	protected Random rand;
	protected WorldVariables vars;	
	protected boolean debug = false;
	protected EvidenceHandler evidenceHandler;
	protected HashMap<Integer,Boolean> evidence;
	protected boolean useUnitPropagation = false;
	Iterable<? extends edu.tum.cs.logic.sat.Clause> kb;
	protected ParameterHandler paramHandler;
	/**
	 * SampleSAT's p parameter: probability of performing a random walk (WalkSAT-style) move rather than a simulated annealing-style move
	 */
	protected double pSampleSAT = 0.9; // 0.5
	
	/**
	 * WalkSAT's p parameter: random walk parameter, probability of non-greedy move (random flip in unsatisfied clause) rather than greedy (locally optimal) move.
	 * According to the WalkSAT paper, optimal values were always between 0.5 and 0.6
	 */
	protected double pWalkSAT = 0.5; // 0.5
	
	
	/**
	 * @param kb a collection of clauses to satisfy (such as a ClausalKB)
	 * @param state a possible world to write to (can be arbitrarily initialized, as it is completely reinitialized)
	 * @param vars the set of variables the SAT problem is defined on
	 * @param db an evidence database indicating truth values of evidence atoms (which are to be respected by the algorithm); the state is initialized to respect it and the respective variables are never touched again
	 * @throws Exception
	 */
	public SampleSAT(Iterable<? extends edu.tum.cs.logic.sat.Clause> kb, PossibleWorld state, WorldVariables vars, Iterable<? extends AbstractVariable> db) throws Exception {
		this.state = state;
		this.vars = vars;
		this.kb = kb;
		rand = new Random();
		constraints = null;		
		
		// parameter handling
		paramHandler = new ParameterHandler(this);
		paramHandler.add("pSampleSAT", "setPSampleSAT");
		paramHandler.add("pWalkSAT", "setPWalkSAT");
		
		// read evidence
		evidenceHandler = new EvidenceHandler(vars, db);
		evidence = evidenceHandler.getEvidence();
	}

	/**
	 * initializes the sampler without a set of constraints 
	 * @param state a possible world to write to (can be arbitrarily initialized, as it is completely reinitialized)
	 * @param vars the set of variables the SAT problem is defined on
	 * @param db an evidence database indicating truth values of evidence atoms (which are to be respected by the algorithm); the state is initialized to respect it and the respective variables are never touched again
	 * @throws Exception
	 */
	public SampleSAT(PossibleWorld state, WorldVariables vars, Iterable<? extends AbstractVariable> db) throws Exception { 
		this(null, state, vars, db);
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}

	/**
	 * enables unit propagation when initializing the set of constraints
	 */
	public void enableUnitPropagation() {
		useUnitPropagation = true;
	}
	
	/**
	 * prepares this sampler for a new set of constraints. NOTE: This method only needs to be called explicitly when switching to a new set of constraints or when using the construction method without the KB
	 * @param kb
	 * @throws Exception 
	 */
	public void initConstraints(Iterable<? extends edu.tum.cs.logic.sat.Clause> kb) throws Exception {
		// if constraints were previously instantiated, check whether a reinstantiation is allowed
		if(constraints != null && useUnitPropagation)
			throw new Exception("Resetting the set of constraints is not allowed when using unit propagation, because unit propagation extends the evidence database, which currently cannot be reversed.");
		this.kb = kb;
		
		// initialize data structures for constraints (used during algorithm) 
		unsatisfiedConstraints = new Vector<Constraint>();
		bottlenecks = new HashMap<Integer,Vector<Constraint>>();		
		
		// build constraint data
		constraints = new Vector<Constraint>();
		GAOccurrences = new HashMap<Integer,Vector<Constraint>>();
		for(edu.tum.cs.logic.sat.Clause c : kb) 
			constraints.add(new Clause(c.lits));
		
		// preprocessing
		if(useUnitPropagation)
			unitPropagation(); // may extend evidence
		
		// set evidence in state
		evidenceHandler.setEvidenceInState(state);
	}
	
	/**
	 * performs unit propagation on clauses to simplify the set of constraints
	 */
	protected void unitPropagation() {
		int oldSize = constraints.size();
		LinkedList<Clause> unitClauses = new LinkedList<Clause>();
		for(Constraint c : constraints) {
			if(c instanceof Clause) {
				Clause cl = (Clause)c;
				if(cl.size() == 1)
					unitClauses.add(cl);
			}
		}
		while(!unitClauses.isEmpty()) {
			Clause cl = unitClauses.remove();
			GroundLiteral lit = cl.getLiterals()[0]; 
			evidence.put(lit.gndAtom.index, lit.isPositive);
			Vector<Constraint> affected = GAOccurrences.get(lit.gndAtom.index);
			if(affected != null) {
				Vector<Clause> scheduledForRemoval = new Vector<Clause>();
				for(Constraint c : affected) {
					if(c instanceof Clause) {
						Clause acl = (Clause)c;
						for(GroundLiteral l : acl.getLiterals()) {
							if(l.gndAtom.index == lit.gndAtom.index) {
								if(l.isPositive == lit.isPositive) // the affected clause is always true because the unit clause appears as a subset
									scheduledForRemoval.add(acl); // schedule for removal to avoid ConcurrentModificationExceptions
								else { // otherwise the literal in the clause is false and we can remove it
									acl.removeLiteral(lit.gndAtom.index);
									if(acl.size() == 1)
										unitClauses.add(acl);
									if(acl.size() == 0)
										constraints.remove(acl);
								}
							}
						}
					}
				}
				for(Clause acl : scheduledForRemoval)
					removeClause(acl);
			}
			// remove the unit clause from the set of constraints
			constraints.remove(cl);
			// we no longer need the occurrences entry
			GAOccurrences.remove(lit.gndAtom.index);
		}
		int newSize = constraints.size();
		if(debug || true) System.out.println("unit propagation removed " + (oldSize-newSize) + " constraints");
	}
	
	protected void removeClause(Clause c) {
		constraints.remove(c);
		// remove dangling references
		for(GroundLiteral lit : c.getLiterals()) 
			GAOccurrences.get(lit.gndAtom.index).remove(c);
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
	
	/**
	 * solves the SAT problem by first initializing the state randomly (respecting the evidence, however) and then performing greedy and SA moves (as determined by parameter p)  
	 * @throws Exception 
	 */
	public void run() throws Exception {		
		// instantiate constraints
		if(constraints == null)
			initConstraints(kb);

		// gather constraint data
		bottlenecks.clear();
		unsatisfiedConstraints.clear();
		if(debug) System.out.println("setting random state...");
		setRandomState();
		if(debug) state.print();
		for(Constraint c : constraints)
			c.initState();
		
		int step = 1;
		while(unsatisfiedConstraints.size() > 0) {
			// debug code
			if(debug) {				
				System.out.println("SAT step " + step + ", " + unsatisfiedConstraints.size() + " constraints unsatisfied");
				if(true) {
					//state.print();
					if(unsatisfiedConstraints.size() < 30)
						for(Constraint c : unsatisfiedConstraints) {
							System.out.println("  unsatisfied: " + c);
						}
				}
				checkIntegrity();
			}
			
			makeMove();
			step++;
		}
	}
	
	/**
	 * checks the integrity of internal data structures
	 * @throws Exception 
	 */
	protected void checkIntegrity() throws Exception {		
		// - are unsatisfied constraints really unsatisfied?
		for(Constraint c : this.constraints) {
			if(c instanceof Clause) {
				Clause cl = (Clause)c;						
				int numTrue = 0;
				for(GroundLiteral lit : cl.lits)
					if(lit.isTrue(state)) {
						numTrue++;
						if(!cl.trueOnes.contains(lit.gndAtom))
							throw new Exception("Clause.trueOnes corrupted (1)");
					}
				if(numTrue != cl.trueOnes.size())
					throw new Exception("Clause.trueOnes corrupted (2)");
				boolean isTrue = numTrue > 0;
				boolean contained = unsatisfiedConstraints.contains(c);
				if(contained != !isTrue)
					throw new Exception("Unsatisfied constraints corrupted");
			}
		}
		// - are bottlenecks really bottlenecks?
		for(java.util.Map.Entry<Integer,Vector<Constraint>> entry : bottlenecks.entrySet()) {
			GroundAtom ga = this.vars.get(entry.getKey());
			for(Constraint c : entry.getValue()) {
				if(c instanceof Clause) {
					Clause cl = (Clause)c;
					boolean haveTrueOne = false;
					for(GroundLiteral lit : cl.lits) {
						if(lit.isTrue(state)) {
							if(haveTrueOne) 
								throw new Exception("Bottlenecks corrupted: Clause " + cl + " contains a second true literal.");
							if(lit.gndAtom != ga)
								throw new Exception("Bottlenecks corrupted: Clause " + cl + " contains a true literal that isn't the bottleneck.");							
							haveTrueOne = true;
						}
						if(lit.gndAtom == ga && !lit.isTrue(state))
							throw new Exception("Bottlenecks corrupted: Clause " + cl + " has " + ga + " as a bottleneck but contains a literal with " + ga + " that is false; it is likely that the clause is a tautology which should never have bottlenecks.");
					}
				}
			}
		}
	}
	
	public PossibleWorld getState() {
		return state;
	}
	
	/**
	 * sets a random state for non-evidence atoms
	 */
	protected void setRandomState() {
		evidenceHandler.setRandomState(state);
	}
	
	protected void makeMove() {
		if(rand.nextDouble() < this.pSampleSAT) {
			if(debug) System.out.println("  WalkSAT move:");
			walkSATMove();
		}
		else {
			if(debug) System.out.println("  SA move:");
			SAMove();
		}
	}
	
	protected void walkSATMove() {
		// pick an unsatisfied constraint
		Constraint c = unsatisfiedConstraints.get(rand.nextInt(unsatisfiedConstraints.size()));
		// with probability p, satisfy the constraint randomly		
		if(rand.nextDouble() < this.pWalkSAT)
			c.satisfyRandomly(); 
		// with probability 1-p, satisfy it greedily
		else
			c.satisfyGreedily();
	}
	
	protected void SAMove() {
		boolean done = false;
		while(!done) {
			// randomly pick a ground atom to flip
			int idxGA = rand.nextInt(vars.size());
			GroundAtom gndAtom = vars.get(idxGA);
			// if it has evidence, skip it
			if(evidence.containsKey(idxGA))
				continue;
			// try to flip it (along with a second one, where appropriate)
			done = pickSecondAtRandomAndFlip(gndAtom);
		}	
	}
	
	/**
	 * attempts to flip the variable that is given, choosing an appropriate second variable (at random where applicable) if the variable is in a block
	 * @param gndAtom
	 * @return true if the variable could be flipped
	 */
	protected boolean pickSecondAtRandomAndFlip(GroundAtom gndAtom) {
		// if it's in a block, must choose a second to flip
		GroundAtom gndAtom2 = null;
		Block block = vars.getBlock(gndAtom.index);
		if(block != null) {				
			GroundAtom trueOne = block.getTrueOne(state);
			if(gndAtom == trueOne) { // if we are flipping the true one, pick the second at random among the others
				Vector<GroundAtom> others = new Vector<GroundAtom>();
				for(GroundAtom ga : block) {
					if(ga != trueOne && !evidence.containsKey(ga.index))
						others.add(ga);
				}
				if(others.isEmpty())
					return false;
				gndAtom2 = others.get(rand.nextInt(others.size()));
			}
			else { // second to flip must be true one
				if(evidence.containsKey(trueOne.index))
					return false;
				gndAtom2 = trueOne;
			}
		}
		// flip
		flipGndAtom(gndAtom);
		if(gndAtom2 != null)
			flipGndAtom(gndAtom2);
		return true;		
	}
	
	protected void pickAndFlipVar(Iterable<GroundAtom> candidates) {
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
		if(debug) System.out.println("  flipping " + gndAtom);
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
		Vector<Constraint> occs = this.GAOccurrences.get(gndAtom.index);
		if(occs != null)
			for(Constraint c : occs)
				if(c.flipSatisfies(gndAtom))
					delta++; 
		return delta;
	}
	
	/**
	 * sets the probability of a random walk (WalkSAT-style) move
	 * @param p
	 */
	public void setPSampleSAT(double p) {
		this.pSampleSAT = p;
	}
	
	/**
	 * sets the probability of a random move (rather than a greedy move) in WalkSAT moves
	 * @param p
	 */
	public void setPWalkSAT(double p) {
		this.pWalkSAT = p;
	}
	
	protected abstract class Constraint {
		public abstract void satisfyGreedily();
		public abstract void satisfyRandomly();
		public abstract boolean flipSatisfies(GroundAtom gndAtom);
		public abstract void handleFlip(GroundAtom gndAtom);
		public abstract void initState();
		public abstract boolean isTrue(PossibleWorld w);
	}
	
	protected class Clause extends Constraint {
		protected GroundLiteral[] lits;
		protected Vector<GroundAtom> gndAtoms;
		protected HashSet<GroundAtom> trueOnes;
		
		public Clause(GroundLiteral[] lits) {	
			this.lits = lits;
			// collect ground atom occurrences 
			gndAtoms = new Vector<GroundAtom>(lits.length);
			trueOnes = new HashSet<GroundAtom>((lits.length+1)/2);
			for(GroundLiteral lit : lits) {
				GroundAtom gndAtom = lit.gndAtom;
				gndAtoms.add(gndAtom);
				addGAOccurrence(gndAtom, this);
			}
		}
		
		public boolean isTrue(PossibleWorld w) {
			for(GroundLiteral lit : lits)
				if(lit.isTrue(w))
					return true;
			return false;
		}
		
		@Override
		public void satisfyGreedily() {
			pickAndFlipVar(gndAtoms);
		}
		
		public void satisfyRandomly() {
			boolean done = false;
			while(!done) {
				// randomly pick a ground atom from the clause to flip
				GroundAtom gndAtom = this.gndAtoms.get(rand.nextInt(this.gndAtoms.size()));
				// if it has evidence, skip it
				if(evidence.containsKey(gndAtom.index))
					continue;
				// try to flip it (along with a second one, where appropriate)
				done = pickSecondAtRandomAndFlip(gndAtom);
			}
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
			// (unless the clause also contains the negated literal, 
			// but a sat.Clause guarantees that this cannot be the case)
			else if(trueOnes.size() == 1) 
				addBottleneck(trueOnes.iterator().next(), this);			
		}
		
		public int size() {
			return this.lits.length;
		}

		public GroundLiteral[] getLiterals() {
			return lits;
		}
		
		public void removeLiteral(int idxGndAtom) {
			GroundLiteral[] newlits = new GroundLiteral[this.lits.length-1];
			gndAtoms.clear();
			for(int i = 0, j = 0; i < lits.length; i++)
				if(lits[i].gndAtom.index != idxGndAtom) {
					newlits[j++] = lits[i];
					gndAtoms.add(lits[i].gndAtom);
				}
			lits = newlits;
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
		BayesianLogicNetwork bln = new BayesianLogicNetwork(new ABLModel(blog, net), blnfile);
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
		SampleSAT ss = new SampleSAT(ckb, state, gbln.getWorldVars(), gbln.getDatabase().getEntries());
		ss.run();
		sw.stop();
		/*System.out.println("SECOND RUN");
		ss.run();*/
		System.out.println("done");
		state.print();
		System.out.println("time taken: " + sw.getElapsedTimeSecs());		
	}

	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	public String getAlgorithmName() {
		return String.format("%s[%f;%f]", this.getClass().getSimpleName(), pSampleSAT, pWalkSAT);
	}
}
