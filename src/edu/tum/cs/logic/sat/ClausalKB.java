package edu.tum.cs.logic.sat;

import java.util.Iterator;
import java.util.Vector;

import edu.tum.cs.logic.Conjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.TrueFalse;

public class ClausalKB implements Iterable<Clause> {
	
	protected Vector<Clause> clauses;
	
	public ClausalKB(edu.tum.cs.logic.KnowledgeBase kb) throws Exception {
		// obtain clausal form
		clauses = new Vector<Clause>();
		for(Formula f : kb) 
			addFormula(f);		
	}
	
	public void addFormula(Formula f) throws Exception {
		//System.out.println("formula: " + f.toString());
		f = f.toCNF();
		//System.out.println("cnf formula: " + f.toString());
		if(f instanceof Conjunction) {
			Conjunction c = (Conjunction)f;
			for(Formula child : c.children)
				clauses.add(new Clause(child));
		}
		else {
			if(f instanceof edu.tum.cs.logic.TrueFalse) {
				if(!((TrueFalse)f).isTrue())
					throw new Exception("Knowledge base is unsatisfiable!");
			}
			else
				clauses.add(new Clause(f));
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
