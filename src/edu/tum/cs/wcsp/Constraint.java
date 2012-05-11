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
	protected HashMap<int[], Long> tuples;
	protected int[] varIndices;
	protected long defaultCost;
	
	public Constraint(long defaultCost, int[] varIndices, int initialTuples) {
		this.varIndices = varIndices;
		this.defaultCost = defaultCost;
		tuples = new HashMap<int[], Long>();
	}
	
	public void addTuple(int[] domainIndices, long cost) {
		tuples.put(domainIndices, cost);
	}
	
	public long getCost(int[] domainIndices) {
		Long cost = tuples.get(domainIndices);
		if(cost == null)
			return defaultCost;
		return cost;
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
		for(Entry<int[], Long> e : tuples.entrySet()) {
			for(int domIdx : e.getKey()) {
				out.print(domIdx);
				out.print(' ');				
			}
			out.println(e.getValue());
		}
	}
}
