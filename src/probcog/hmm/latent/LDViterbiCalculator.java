package probcog.hmm.latent;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;

import probcog.hmm.IObservationModel;
import probcog.hmm.ViterbiCalculator;


import be.ac.ulg.montefiore.run.jahmm.ObservationVector;


/**
 * This class can be used to compute the most probable state sequence matching
 * a given observation sequence (given a latent dynamic HMM (LD-HMM)).
 * @author jain
 */
public class LDViterbiCalculator<O extends ObservationVector>
{			
	protected LDHMM hmm;	
	protected Path viterbiPath;	
	protected int step = 0;
	protected Map<State,Path> prev;
	
	// debugging stuff
	protected boolean verbose = true;
	protected final boolean debug = false;
	Vector<Map<State,Path>> paths = null;
	
	public class Path implements Comparable<Path> {		
		public State s;
		public int step;
		public Path predecessor, segmentStart;
		public double delta, lpLastSwitch, lpStateTransitionsFromSwitch;
		public ViterbiCalculator<ObservationVector> vc;
		protected IObservationModel<ObservationVector> fc;
		
		protected static final boolean subComputationViterbi = false; 
		
		/**
		 * debug values
		 */
		public double pTrans, pObs;
		public Vector<Path> successors = null;
		
		/**
		 * constructs a path object that represents the start of a new segment
		 * @param s
		 */
		protected Path(State s, Path predecessor) {
			this.s = s;
			this.segmentStart = this;
			if(subComputationViterbi) {
				if(hmm.subHMMs[s.label] instanceof SubHMMSimple)
					vc = new ViterbiCalculator<ObservationVector>((SubHMMSimple)hmm.subHMMs[s.label]);
				else
					throw new RuntimeException("no longer supported");
			}
			else
				fc = hmm.getObservationModel(s.label);
			this.predecessor = predecessor;
			if(predecessor == null) {
				this.step = 0;
				this.lpLastSwitch = 0;				
			}
			else {
				this.step = predecessor.step+1;
				this.lpLastSwitch = predecessor.delta;				
			}
			this.lpStateTransitionsFromSwitch = 0;
		}
		
		/**
		 * constructs a new path, i.e. the very beginning of a path
		 * @param label initial label
		 * @param observation initial observation
		 */
		public Path(int label, O observation) {
			this(new State(label, 0), null);
			double lpObs;
			if(subComputationViterbi)
				lpObs = vc.step(observation);			
			else  {
				pObs = fc.getObservationProbability(observation);
				lpObs = Math.log(pObs);
			}
			pTrans = hmm.getPi(s.label);
			lpStateTransitionsFromSwitch += Math.log(pTrans); 
			
			delta = lpStateTransitionsFromSwitch + lpObs;
		}
		
		/**
		 * constructs a path that results when continuing the given path with the same segment
		 * @param p the path to continue
		 */
		protected Path(Path p) {
			this.s = new State(p.s.label, p.s.dwellTime+1);
			this.predecessor = p;
			this.step = predecessor.step+1;
			this.segmentStart = p.segmentStart;
			this.vc = p.vc;
			this.fc = p.fc;
			this.lpStateTransitionsFromSwitch = p.lpStateTransitionsFromSwitch;
			this.lpLastSwitch = p.lpLastSwitch;
		}
		
		public Path proceed(int label, O observation) {
			Path p2;			
			if(label == -1) // remaining in same segment
				p2 = new Path(this);			
			else // switching to different state
				p2 = new Path(new State(label, 0), this);			
						
			// debugging: store successor information
			if(debug /*&& p2.step > 950*/) { // NOTE: causes severe slowdown				
				if(this.successors == null)
					this.successors = new Vector<Path>();
				this.successors.add(p2);
			}
			
			// update probabilities
			p2.pTrans = this.s.getTransitionProbability(hmm, label);
			double lpTrans = Math.log(p2.pTrans);
			p2.lpStateTransitionsFromSwitch += lpTrans;
			if(subComputationViterbi) {				
				double lpPath = p2.vc.step(observation);
				p2.delta = p2.lpLastSwitch + p2.lpStateTransitionsFromSwitch + lpPath;
			}
			else {
				p2.pObs = p2.fc.getObservationProbability(observation);
				double lpObs = Math.log(p2.pObs); 				
				p2.delta = this.delta + lpTrans + lpObs;
			}
			
			return p2;
		}
		
		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			Path p = this;
			do {
				sb.append(p.s.toString());
				sb.append(" <- ");
				p = p.predecessor;
			} while(p != null);
			return sb.toString();
		}

		@Override
		public int compareTo(Path o) {
			return -Double.compare(this.delta, o.delta); // sort in descending order of delta/probability	
		}
	}

	
	/**
	 * Computes the most likely state sequence matching an observation
	 * sequence given an HMM.
	 *
	 * @param hmm A Hidden Markov Model;
	 * @param oseq An observations sequence.
	 */
	public LDViterbiCalculator(LDHMM hmm) {
		this.hmm = hmm;		
		if(debug) paths = new Vector<Map<State,Path>>();		
	}
	
	public void run(Iterable<? extends O> oseq, int maxStep) {
		for(O observation : oseq) {			
			step(observation);
			if(step == maxStep)
				break;
		}
		if(verbose) System.out.println();

		// debugging: for each time step compute ordered list of paths
		Vector<Vector<Path>> sortedPaths = null;
		if(debug) { 
			sortedPaths = new Vector<Vector<Path>>();
			for(Map<State,Path> m : paths) {
				Vector<Path> v = new Vector<Path>(m.values());
				Collections.sort(v);
				sortedPaths.add(v);
			}
		}		
	}
	
	public void run(Iterable<? extends O> oseq) {
		run(oseq, -1);
	}
	
	protected void step(O observation) {
		viterbiPath = null;
		
		if(step == 0) {
			
			prev = new HashMap<State, Path>(); 
			for(int i = 0; i < hmm.getNumStates(); i++) {
				Path p = new Path(i, observation);
				prev.put(p.s, p);
				if(viterbiPath == null || p.delta > viterbiPath.delta) 
					viterbiPath = p;
			}
			if(debug) paths.add(prev);
			
		}
		else {
			
			Map<State, Path> cur = new HashMap<State, Path>();
			int infinite = 0, nan = 0;
			for(Path p1 : prev.values()) {
				for(int j = -1; j < hmm.getNumStates(); j++) {					
					Path p2 = p1.proceed(j, observation);				
					double delta = p2.delta;
					if(Double.isInfinite(delta)) {
						infinite++;
						continue;
					}
					if(Double.isNaN(delta)) {
						nan++;
						continue;
					}
					Path best = cur.get(p2.s);
					if(best == null || delta > best.delta) {
						cur.put(p2.s, p2);
						if(viterbiPath == null || p2.delta > viterbiPath.delta) 
							viterbiPath = p2;
						//System.out.println(p2);
					}
				}
			}
			
			// debugging: keep path information
			if(debug)
				paths.add(cur);
			
			// done, proceed to next step
			prev = cur;
			if(verbose) 
				System.out.printf("Viterbi step %d: %d paths (%d infinite, %d NaN)      \r", step, cur.size(), infinite, nan);
			if(cur.size() == 0)
				throw new RuntimeException("No paths with finite probability remain");

		}
		step++;		
	}
	
	public double getViterbiPathLogProbability() {
		return viterbiPath.delta;
	}
	
	/**
	 * @return the Viterbi path (most likely hidden state sequence)
	 */
	public LinkedList<Integer> getViterbiPath() 
	{
		LinkedList<Integer> stateSequence = new LinkedList<Integer>();		
		Path p = viterbiPath;
		do {
			stateSequence.addFirst(p.s.label);
			p = p.predecessor;
		} while(p != null);
		return stateSequence;		
	}
}
