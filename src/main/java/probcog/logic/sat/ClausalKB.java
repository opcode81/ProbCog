/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.logic.sat;

import java.util.Iterator;
import java.util.Vector;

import probcog.exception.ProbCogException;
import probcog.logic.Conjunction;
import probcog.logic.Formula;
import probcog.logic.TrueFalse;
import probcog.logic.sat.Clause.TautologyException;

/**
 * Represents a logical knowledge base made up of clauses.
 * @author Dominik Jain
 */
public class ClausalKB implements Iterable<Clause> {
	
	protected Vector<Clause> clauses;
	
	/**
	 * creates a clausal KB from a given (non-clausal) KB
	 * @param kb
	 * @throws ProbCogException
	 */
	public ClausalKB(probcog.logic.KnowledgeBase kb) throws ProbCogException {
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
	 * @throws ProbCogException
	 */
	public void addFormula(Formula f) throws ProbCogException {
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
					throw new ProbCogException("Knowledge base is unsatisfiable!");
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
