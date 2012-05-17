package probcog.logic.sat;

import java.util.Iterator;
import java.util.Vector;

import probcog.logic.Conjunction;
import probcog.logic.Formula;
import probcog.logic.TrueFalse;
import probcog.logic.sat.Clause.TautologyException;


public class ClausalKB implements Iterable<Clause> {
	
	protected Vector<Clause> clauses;
	
	/**
	 * creates a clausal KB from a given (non-clausal) KB
	 * @param kb
	 * @throws Exception
	 */
	public ClausalKB(probcog.logic.KnowledgeBase kb) throws Exception {
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
			if(f instanceof probcog.logic.TrueFalse) {
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
