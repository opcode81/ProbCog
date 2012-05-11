/*
 * Created on May 11, 2012
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.wcsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
	
	public static WCSP fromFile(File f) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(f));
		String l = br.readLine();
		String[] elems = l.split(" ");
		int numVars = Integer.parseInt(elems[1]);
		int numConstraints = Integer.parseInt(elems[3]);
		long top = Integer.parseInt(elems[4]);
		elems = br.readLine().split(" ");
		int[] domSizes = new int[numVars];
		for(int i = 0; i < numVars; i++)
			domSizes[i] = Integer.parseInt(elems[i]);
		WCSP wcsp = new WCSP(numVars, domSizes, top);
		for(int i = 0; i < numConstraints; i++) {
			elems = br.readLine().split(" ");
			int arity = Integer.parseInt(elems[0]);
			int[] varIndices = new int[arity];
			for(int j = 1; j <= arity; j++)
				varIndices[j-1] = Integer.parseInt(elems[j]);
			long defaultCost = Long.parseLong(elems[1+arity]);
			int numTuples = Integer.parseInt(elems[1+arity+1]);
			Constraint c = new Constraint(defaultCost, varIndices, numTuples);
			for(int j = 0; j < numTuples; j++) {
				elems = br.readLine().split(" ");
				int[] assignment = new int[arity];
				for(int k = 0; k < arity; k++)
					assignment[k] = Integer.parseInt(elems[k]);
				long cost = Long.parseLong(elems[arity]);
				c.addTuple(assignment, cost);
			}
			wcsp.addConstraint(c);
		}
		return wcsp;
	}
}
