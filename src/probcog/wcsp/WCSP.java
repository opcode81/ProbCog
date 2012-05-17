/*
 * Created on May 11, 2012
 */
package probcog.wcsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.AbstractList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.tum.cs.util.Stopwatch;

public class WCSP implements Iterable<Constraint> {
	protected long top;
	protected List<Constraint> constraints;
	protected int numVariables;
	protected int[] domainSizes;
	protected int maxDomSize;
	
	public WCSP(int[] domainSizes, long top) {
		constraints = new LinkedList<Constraint>();
		this.numVariables = domainSizes.length;
		this.domainSizes = domainSizes;
		maxDomSize = 0;
		for(int i = 0; i < numVariables; i++)
			maxDomSize = Math.max(maxDomSize, domainSizes[i]);
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
	
	public int size() {
		return constraints.size();
	}
	
	/**
	 * unifies constraints with the same domain, reducing the total number of constraints
	 */
	public void unifyConstraints() {
		HashMap<Set<Integer>, Constraint> existingConstraints = new HashMap<Set<Integer>, Constraint>(); 
		Iterator<Constraint> i = this.iterator();
		while(i.hasNext()) {
			Constraint c2 = i.next();
			final int[] c2varIndices = c2.getVarIndices();
			Set<Integer> key = new HashSet<Integer>(new AbstractList<Integer>() {
				@Override
				public Integer get(int index) {					
					return c2varIndices[index];
				}
				@Override
				public int size() {
					return c2varIndices.length;
				}				
			});			
			Constraint c1 = existingConstraints.get(key);
			if(c1 != null) {
				c1.mergeReorder(c2);
				i.remove();
			}
			else {
				existingConstraints.put(key, c2);
			}
		}
	}
	
	public static WCSP fromFile(File f) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(f));
		String l = br.readLine();
		String[] elems = l.split(" ");
		int numVars = Integer.parseInt(elems[1]);
		int numConstraints = Integer.parseInt(elems[3]);
		long top = Long.parseLong(elems[4]);
		elems = br.readLine().split(" ");
		int[] domSizes = new int[numVars];
		for(int i = 0; i < numVars; i++)
			domSizes[i] = Integer.parseInt(elems[i]);
		WCSP wcsp = new WCSP(domSizes, top);
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
	
	/**
	 * writes a toulbar2-style wcsp file
	 * @param out the stream to write to
	 * @param name the name of this WCSP
	 */
	public void writeWCSP(PrintStream out, String name) {
        // the first line of the WCSP-File
        // syntax: name of the WCSP, number of variables, maximum domain size of the variables, number of constraints, initial TOP
    	out.printf("%s %d %d %d %d\n", name, getNumVariables(), this.maxDomSize, constraints.size(), top);
    	// second line: domain sizes
    	for(int i = 0; i < numVariables; i++) {
    		out.print(domainSizes[i]);
    		out.print(' ');
    	}
    	out.println();
    	// write constraints
    	for(Constraint c : this)
    		c.writeWCSP(out);
	}
	
	public static void main(String[] args) {
		WCSP wcsp;
		try {
			//File inFile = new File("/home/nyga/code/prac/models/filling/temp.wcsp");
			File inFile = new File("/usr/wiss/nyga/temp.wcsp");
			wcsp = WCSP.fromFile(inFile);
			Stopwatch sw = new Stopwatch();
			sw.start();
			wcsp.unifyConstraints();
			System.out.println("unification time: " + sw.getElapsedTimeSecs());
			//wcsp.writeWCSP(new PrintStream("/home/nyga/code/prac/models/filling/unified.wcsp"), "unifiedWCSP");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
