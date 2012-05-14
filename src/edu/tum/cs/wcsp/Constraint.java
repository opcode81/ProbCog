/*
 * Created on May 11, 2012
 */
package edu.tum.cs.wcsp;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

/**
  * @author jain
  */
public class Constraint {
	protected HashMap<TupleKey, Tuple> tuples;
	/**
	 * array of variable indices references by this constraint; for technical reasons (required e.g. by
	 * {@link WCSP.unifyConstraints}) it is sorted
	 */
	protected int[] varIndices;
	protected long defaultCost;
	
	public Constraint(long defaultCost, int[] varIndices, int initialTuples) {
		this.varIndices = varIndices;
		Arrays.sort(this.varIndices);
		this.defaultCost = defaultCost;
		tuples = new HashMap<TupleKey, Tuple>();
	}
	
	public void addTuple(int[] domainIndices, long cost) {
		tuples.put(new TupleKey(domainIndices), new Tuple(domainIndices, cost));
	}
	
	public void addTuple(Tuple t) {
		tuples.put(new TupleKey(t.domIndices), t);
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
	
	public Tuple getTuple(int[] setting) {
		return tuples.get(new TupleKey(setting));
	}
	
	public Tuple getTuple(TupleKey k) {
		return tuples.get(k);
	}
	
	public long getDefaultCosts() {
		return defaultCost;
	}
	
	public void setDefaultCosts(long c) {
		this.defaultCost = c;
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
		for(Entry<TupleKey, Tuple> e : tuples.entrySet()) {
			Tuple t = e.getValue();
			for(int domIdx : t.domIndices) {
				out.print(domIdx);
				out.print(' ');				
			}
			out.println(t.cost);
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
	
	protected static class TupleKey {
		protected int[] domIndices; 
		
		public TupleKey(int[] domIndices) {
			this.domIndices = domIndices;
		}
		
		@Override
		public int hashCode() {
			return Arrays.hashCode(domIndices);
		}
		
		public boolean equals(Object o) {
			if(!(o instanceof TupleKey))
				return false;
			return Arrays.equals(this.domIndices, ((TupleKey)o).domIndices);
		}
	}
}
