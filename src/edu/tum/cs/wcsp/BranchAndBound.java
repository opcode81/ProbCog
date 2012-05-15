package edu.tum.cs.wcsp;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.datastruct.Map2Set;
import edu.tum.cs.util.datastruct.Map2Stack;
import edu.tum.cs.wcsp.Constraint.Tuple;

/**
 * simple, mostly naive implementation of branch and bound search
 * @author jain
 * @author nyga
 */
public class BranchAndBound {

	protected long upperBound = 0L;
	protected WCSP wcsp;
	protected Map2Set<Integer,Constraint> varIdx2constraint = new Map2Set<Integer,Constraint>();
	protected SearchStack searchStack = null;
	protected long bestSolutionCosts;
	
	
	public BranchAndBound(WCSP wcsp, long initialUpperBound) {
		this.wcsp = wcsp;		
		this.upperBound = initialUpperBound;
		
		for(Constraint c : wcsp) {
			int[] varIndices = c.getVarIndices();
			for(int i : varIndices)
				varIdx2constraint.add(i, c);
		}
	}
	
//	protected int[] getVariableOrder() {
//		
//		TreeMap<Long, Integer> varOrder = new TreeMap<Long, Integer>();
//		Map<Integer, Map<Integer, Long>> costs = new HashMap<Integer, Map<Integer>, Long>>();
//		for (Constraint c: wcsp) {
//			HashMap<> 
//			for (Tuple t: c.getTuples()) {
//				
//			}
//		}
//	}
		
	public int[] findSolution() {
		this.searchStack = new SearchStack();		
		Map<Integer,Integer> currentBestSolution = null;
		long currentBestSolutionCosts = Long.MAX_VALUE;

		// get the next variable to assign
		int varIdx = getNextVariable();
		searchStack.push(varIdx);
		
		while(true) {		
			// assign the next value
			int domIdx = getNextValueOfVar(varIdx);
			
			// if there is no next value, go to the previous variable
			if(domIdx == -1) {
				searchStack.pop();				
				if(searchStack.assignmentOrder.isEmpty()) // if there is no previous variable, we are done
					break;
				varIdx = searchStack.assignmentOrder.peek();
				continue;
			}
			
			boolean isOK = searchStack.assign(domIdx);
			if(!isOK) {
				searchStack.undoAssignment();
				continue;
			}
			else {
				// get the next variable to assign
				varIdx = getNextVariable();

				// if there isn't one, we have found a leaf and need to go on to the next value
				if(varIdx == -1) {					
					if(searchStack.lowerBound < currentBestSolutionCosts) {
						currentBestSolution = (Map)searchStack.assignment.clone();
						currentBestSolutionCosts = searchStack.lowerBound;
						upperBound = currentBestSolutionCosts;
						System.out.println("new solution " + currentBestSolution + " with costs " + currentBestSolutionCosts);
					}
					// try the next value of the previous variable
					searchStack.undoAssignment();
					varIdx = searchStack.assignmentOrder.peek();			
				}
				else
					searchStack.push(varIdx);
			}	
		}
		
		if (currentBestSolution == null)
			return null;
		bestSolutionCosts = currentBestSolutionCosts;
		int[] solution = new int[wcsp.getNumVariables()];
		for (int i = 0; i < solution.length; i++)
			solution[i] = currentBestSolution.get(i);
		return solution;
	}
	
	public long getBestSolutionCosts() {
		return bestSolutionCosts;
	}
	
	protected int getNextValueOfVar(int varIdx) {
		int domSize = wcsp.getDomainSize(varIdx);
		for (int i = 0; i < domSize; i++)
			if (! searchStack.valuesTried.peek().contains(i))
				return i;
		return -1;
	}
	
	protected int getNextVariable() {
		for (int i = 0; i < wcsp.getNumVariables(); i++)
			if (! searchStack.assignment.keySet().contains(i))
				return i;
		return -1;
	}
	
	protected class SearchStack {
		HashMap<Integer,Integer> assignment = new HashMap<Integer,Integer>();
		Stack<Integer> assignmentOrder = new Stack<Integer>();
		protected long lowerBound = 0;
		Map2Stack<Constraint,Long> lowerBoundAdditions = new Map2Stack<Constraint,Long>();
		Stack<Set<Integer>> valuesTried = new Stack<Set<Integer>>();
		
		Integer varIdx;	
		
		public SearchStack() {
			
		}
		
		public void push(Integer varIdx) {
			valuesTried.add(new HashSet<Integer>());
			assignmentOrder.add(this.varIdx=varIdx);
		}
		
		public boolean assign(Integer domIdx) {
			assignment.put(varIdx, domIdx);	
			//System.out.println(assignment);
			valuesTried.peek().add(domIdx);
			
			// remove from lower bound all values that are referenced by constraints that have been
			// touched and contain varIdx
			Collection<Constraint> relevantConstraints = varIdx2constraint.get(varIdx);
			if(relevantConstraints != null)
				for(Constraint c : relevantConstraints) {
					Long addition = lowerBoundAdditions.peekDefault(c, 0L); 
					lowerBound -= addition; 
				}
			
			// go through all constraints that contain varIdx and add to lowerBound
			if(relevantConstraints != null)
				for(Constraint c : varIdx2constraint.get(varIdx)) {				
					int numRequiredTuples = 1;
					for(int constraintVarIdx : c.getVarIndices()) {
						if(!assignment.containsKey(constraintVarIdx))
							numRequiredTuples *= wcsp.getDomainSize(constraintVarIdx);
					}
					long min = Long.MAX_VALUE;
					int numPresentTuples = 0;
					for(Tuple t : c.getTuples()) {
						if(t.couldApply(c, assignment)) {
							min = Math.min(min, t.cost);
							++numPresentTuples;
						}
					}
					assert numPresentTuples <= numRequiredTuples;
					if(numPresentTuples < numRequiredTuples)
						min = Math.min(min, c.getDefaultCosts());
					//System.out.printf("adding %d to lower bound for %s\n", min, c);
					lowerBound += min;
					lowerBoundAdditions.push(c, min);
				}
			
			//System.out.println("lower bound: " + lowerBound);
			return lowerBound < upperBound;
		}
		
		public void undoAssignment() {
			// undo assignment						
			assignment.remove(varIdx);			
			
			// restore old state of lower bound additions
			Collection<Constraint> relevantConstraints = varIdx2constraint.get(varIdx);
			if(relevantConstraints != null)
				for(Constraint c : relevantConstraints) {
					lowerBound -= lowerBoundAdditions.pop(c);
					lowerBound += lowerBoundAdditions.peekDefault(c, 0L);
				}
		}
		
		public void pop() {
			int lastAssignedVar = assignmentOrder.pop();
			this.valuesTried.pop();
			assert lastAssignedVar == varIdx;
			varIdx = assignmentOrder.empty() ? -1 : assignmentOrder.peek();
			assignment.remove(lastAssignedVar);
		}
	}
	
	public static void main(String[] args) throws IOException {
	
		/*
		WCSP wcsp = new WCSP(2, new int[]{2,2}, 100);
		
		Constraint c = new Constraint(100, new int[]{0}, 1);
		c.addTuple(new int[]{0}, 1);
		c.addTuple(new int[]{1}, 5);
		wcsp.addConstraint(c);
		c = new Constraint(100, new int[]{0,1}, 2);
		c.addTuple(new int[]{0,0}, 10);
		c.addTuple(new int[]{0,1}, 10);
		c.addTuple(new int[]{1,0}, 10);
		c.addTuple(new int[]{1,1}, 10);
		wcsp.addConstraint(c);
		*/
		
		//WCSP wcsp = WCSP.fromFile(new java.io.File("/usr/wiss/jain/4queens.wcsp"));
		WCSP wcsp = WCSP.fromFile(new java.io.File("/usr/wiss/jain/temp.wcsp"));
		for(Constraint c: wcsp)
			c.writeWCSP(System.out);

		Stopwatch sw = new Stopwatch();
		sw.start();

		
		BranchAndBound bb = new BranchAndBound(wcsp, wcsp.getTop());
		int[] sol = bb.findSolution();
		if (sol == null) 
			System.out.println("No solution was found.");
		else {
			System.out.println("Best solution found:");		
			for (int assignment: sol) 
				System.out.print(assignment + " ");
			System.out.println();
			System.out.println("Solution costs: " + bb.getBestSolutionCosts());
		}
		
		System.out.println("time taken: " + sw.getElapsedTimeSecs());
	}
	
}