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
package probcog.logic;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Vector;

import probcog.srl.GenericDatabase;

import edu.tum.cs.util.StringTool;

/**
 * Represents a logical conjunction.
 * @author Dominik Jain
 */
public class Conjunction extends ComplexFormula {

	public Conjunction(Collection<Formula> children) {
        super(children);
    }

    public Conjunction(Formula... children) {
        super(children);
    }

    public String toString() {
        return "(" + StringTool.join(" ^ ", children) + ")";
    }

    @Override
    public boolean isTrue(IPossibleWorld w) {
        for (Formula child : children)
            if (!child.isTrue(w))
                return false;
        return true;
    }

    @Override
    public Formula toCNF() {
    	/*
    	try{
    	Negation.cnfDepth++; System.out.printf(String.format("%%%dc", Negation.cnfDepth), ' ');
        System.out.println(this);
        */
        if(this.children.length == 1)
        	return this.children[0].toCNF();
        Vector<Formula> clauses = new Vector<Formula>();
        // first convert all children to CNF and eliminate nested conjunctions by collecting all conjuncts centrally
        for (Formula child : this.children) {
            child = child.toCNF();
            if (child instanceof Conjunction) {
                Conjunction conj = (Conjunction) child;
                clauses.addAll(Arrays.asList(conj.children));
            } else if (child instanceof TrueFalse) {
                TrueFalse tf = (TrueFalse) child;
                if (!tf.isTrue())
                    return tf;
            } else
                clauses.add(child);
        }
        // normalize the obtained clauses by eliminating clauses that are supersets of other clauses
        Vector<HashSet<String>> sclauses = new Vector<HashSet<String>>();
        for (Formula clause : clauses) {
            HashSet<String> s = new HashSet<String>();
            if (clause instanceof Disjunction) {
                for (Formula l : ((Disjunction) clause).children)
                    s.add(l.toString());
            } else {
                s.add(clause.toString());
            }
            sclauses.add(s);
        }
        for (int i = 0; i < sclauses.size(); i++) {
            for (int j = i + 1; j < sclauses.size(); j++) {
                HashSet<String> s1 = sclauses.get(i);
                HashSet<String> s2 = sclauses.get(j);
                int iLarger = j;
                if (s1.size() > s2.size()) {
                    HashSet<String> t = s1;
                    s1 = s2;
                    s2 = t;
                    iLarger = i;
                }
                // s1 is the smaller set; check if the larger set s2 contains all its elements
                boolean isSubset = true;
                for (String s : s1)
                    if (!s2.contains(s)) {
                        isSubset = false;
                        break;
                    }
                if (!isSubset)
                    continue;
                // remove the larger set
                sclauses.remove(iLarger);
                clauses.remove(iLarger);
                if (iLarger == j)
                    j--;
                else
                    j = i;
            }
        }
        // return the conjunction of clauses
        if(clauses.size() == 1)
        	return clauses.get(0);
        return new Conjunction(clauses);
        /*
        }        
    	finally {
    		Negation.cnfDepth--;
    	}
    	*/
    }

    @Override
    public Formula toNNF() {
    	Vector<Formula> conjuncts = new Vector<Formula>();
    	for(Formula child : this.children) {
    		Formula newChild = child.toNNF();
    		if(newChild instanceof Conjunction) { // flatten nested conjunction
    			for(Formula nestedChild : ((Conjunction)newChild).children)
    				conjuncts.add(nestedChild);
    		}
    		else
    			conjuncts.add(newChild);			
    	}
    	return new Conjunction(conjuncts);
    }

    /**
     * This method simplifies the formula (atoms that are given by the evidence are evaluated to TrueFalse)
     * @param evidence (evidence of the current szenario)
     * @return returns a Formula simplified by the evidence or an instance of TrueFalse
     */
    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        Vector<Formula> simplifiedChildren = new Vector<Formula>();
        // check for each child, whether an entry in evidenceDB exists
        for (Formula child : this.children) {
            child = child.simplify(evidence);
            // if the child is false, then the complete conjunction is false
            if (child instanceof TrueFalse) {
                if (!((TrueFalse) child).isTrue())
                    return TrueFalse.FALSE;
                else
                    continue;
            } else
                // adds the child to simplified children if it isn't instance of TrueFalse
                simplifiedChildren.add(child);
        }
        // returns the simplified formula if vector isn't empty
        if (!simplifiedChildren.isEmpty())
            return new Conjunction(simplifiedChildren);
        else
            // otherwise return true
            return TrueFalse.TRUE;
    }
}
