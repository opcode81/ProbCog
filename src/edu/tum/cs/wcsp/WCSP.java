/*
 * Created on May 11, 2012
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.wcsp;

import java.util.Iterator;
import java.util.Vector;

public class WCSP implements Iterable<Constraint> {
	protected long top;
	protected Vector<Constraint> constraints;
	protected int numVariables;
	protected int[] domainSizes;
	
	public WCSP(int numVars, int[] domainSizes, long top) {
		constraints = new Vector<Constraint>();
		this.numVariables = numVars;
		this.domainSizes = domainSizes;
		this.top = top;
	}
	
	public int getNumVariables() {
		return numVariables;
	}
	
	public int getDomainSize(int varIdx) {
		return domainSizes[varIdx];
	} 
	
	public void addConstraint(Constraint c) {
		constraints.add(c);
	}
	
	public long getTop() {
		return top;
	}

	@Override
	public Iterator<Constraint> iterator() {
		return constraints.iterator();
	}
}
