/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
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
package probcog.prolog;

import java.util.Collection;
import java.util.HashSet;
import java.util.Vector;

import yprolog.ParseException;

/**
 * Represents a Prolog KB (using the YProlog library). 
 * @author Dominik Jain
 */
public class PrologKnowledgeBase extends yprolog.YProlog {
	public PrologKnowledgeBase() {
		
	}
	
	/**
	 * adds a formula, i.e. a fact or a rule, to the knowledge base, e.g. "person(eve)." or "parent(X,Y) :- father(X,Y)."
	 * @param formula
	 * @throws ParseException
	 */
	public void tell(String formula) throws ParseException {
		this.yp_eng.consultStringFront(formula);
	}
	
	/**
	 * queries the value of an atom or conjunction and returns the value
	 * @param query an atom or conjunction, e.g. a ground atom such as "parent(eve, kain)" or "age(eve, 27)"
	 * @return true if the formula can be proven to be true given the KB, false otherwise
	 * @throws ParseException
	 */
	public boolean ask(String query) throws ParseException {
		return this.yp_eng.setQuery(query);
	}
	
	
	/**
	 * fetches the ground atoms that match the goal atom
	 * @param goal a query such as "parent(eve,X)"
	 * @return a collection of strings of matching ground atoms
	 */
	public Collection<String> fetchAtoms(String goal) {
		HashSet<String> set = new HashSet<String>();
		String res = this.queryToString(goal, 0, "");
		if(res == null)
			return set;		
		for(String s : res.substring(0, res.length()-1).split("\\."))
			set.add(s);
		return set;
	}
	
	/**
	 * fetches the ground atoms (as a vector of argument lists) that match the given goal
	 * @param goalAtom a query such as "parent(eve,X)"
	 * @return a vector of argument lists, where each list corresponds to one ground atom that matches the query
	 */
	@SuppressWarnings("unchecked")
	public Vector<String[]> fetchBindings(String goal) {
		return this.queryToTable(goal, 0, true);
	}
}
