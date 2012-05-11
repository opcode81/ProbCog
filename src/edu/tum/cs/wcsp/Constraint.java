/*
 * Created on May 11, 2012
 */
package edu.tum.cs.wcsp;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map.Entry;

/**
  * @author jain
  */
public class Constraint {
	protected HashMap<int[], Tuple> tuples;
	protected int[] varIndices;
	protected long defaultCost;
	
	public Constraint(long defaultCost, int[] varIndices, int initialTuples) {
		this.varIndices = varIndices;
		this.defaultCost = defaultCost;
		tuples = new HashMap<int[], Tuple>();
	}
	
	public void addTuple(int[] domainIndices, long cost) {
		tuples.put(domainIndices, new Tuple(domainIndices, cost));
	}
	
	public long getCost(int[] domainIndices) {
		Tuple t = tuples.get(domainIndices);
		if(t == null)
			return defaultCost;
		return t.cost;
	}
	
	public int[] getVarIndices() {
		return varIndices;
	}
	
	public java.util.Collection<Tuple> getTuples() {
		return tuples.values();
	}
	
	public long getDefaultCosts() {
		return defaultCost;
	}
	
	public void writeWCSP(PrintStream out) {
		// first line of the constraint: arity of the constraint followed by indices of the variables, default costs, and number of constraint lines
		out.print(varIndices.length);
		out.print(' ');
		for(int varIdx : varIndices) {
			out.print(varIdx);
			out.print(' ');
		}
		out.print(defaultCost);
		out.print(' ');
		out.println(tuples.size());
		for(Entry<int[], Tuple> e : tuples.entrySet()) {
			for(int domIdx : e.getKey()) {
				out.print(domIdx);
				out.print(' ');				
			}
			out.println(e.getValue().cost);
		}
	}
	
	protected class Tuple {
		public int[] domIndices;
		public long cost;
		
		public Tuple(int[] domIndices, long cost) {
			this.cost = cost;
			this.domIndices = domIndices;
		}
		
		public boolean couldApply(java.util.Map<Integer,Integer> partialAssignment) {
			for(int i = 0; i < varIndices.length; i++) {
				Integer a = partialAssignment.get(varIndices[i]);
				if(a != null && domIndices[i] != a)
					return false;
			}
			return true;
		}
	}
}
