package edu.tum.cs.logic.sat;

import java.util.Iterator;
import java.util.Vector;

import edu.tum.cs.logic.Conjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.TrueFalse;
import edu.tum.cs.logic.sat.Clause.TautologyException;

public class ClausalKB implements Iterable<Clause> {
	
	protected Vector<Clause> clauses;
	
	/**
	 * creates a clausal KB from a given (non-clausal) KB
	 * @param kb
	 * @throws Exception
	 */
	public ClausalKB(edu.tum.cs.logic.KnowledgeBase kb) throws Exception {
		// obtain clausal form
		this();
		for(Formula f : kb) 
			addFormula(f);		
	}
	
	/**
	 * creates an empty clausal KB
	 */
	public ClausalKB() {
		clauses = new Vector<Clause>();
	}
	
	/**
	 * adds a formula to the knowledge bases, converting it into CNF
	 * @param f
	 * @throws Exception
	 */
	public void addFormula(Formula f) throws Exception {
		//System.out.println("formula: " + f.toString());
		f = f.toCNF();
		//System.out.println("cnf formula: " + f.toString());
		if(f instanceof Conjunction) {
			Conjunction c = (Conjunction)f;
			for(Formula child : c.children) {
				try {
					clauses.add(new Clause(child));
				}
				catch(TautologyException e) {} // simply skip tautologies
			}
		}
		else {
			if(f instanceof edu.tum.cs.logic.TrueFalse) {
				if(!((TrueFalse)f).isTrue())
					throw new Exception("Knowledge base is unsatisfiable!");
			}
			else {
				try {
					clauses.add(new Clause(f));
				}
				catch(TautologyException e) {} // simply skip tautologies
			}
		}
	}

	public Iterator<Clause> iterator() {		
		return clauses.iterator();
	}
	
	public int size() {
		return clauses.size();
	}
	
	public void print() {
		int i = 0;
		for(Clause c : this) {
			System.out.printf("%4d  %s\n", ++i, c.toString());
		}
	}
}
