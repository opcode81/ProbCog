/*
 * Created on May 11, 2012
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.wcsp;

import java.util.Vector;

public class WCSP {
	protected long top;
	protected Vector<Constraint> constraints;
	
	public WCSP() {
		constraints = new Vector<Constraint>();
	}
	
	public void addConstraint(Constraint c) {
		constraints.add(c);
	}
	
	public long getTop() {
		return top;
	}
}
