	/*******************************************************************************
 * Copyright (C) 2009-2012 Ralf Wernicke, Dominik Jain.
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
package probcog.logic.sat.weighted;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import probcog.logic.ComplexFormula;
import probcog.logic.Conjunction;
import probcog.logic.Formula;
import probcog.logic.TrueFalse;
import probcog.logic.sat.Clause.TautologyException;


/**
 * A knowledge base of weighted clauses that is built up from general weighted formulas (retaining a mapping from formulas to clauses and vice versa) 
 * @author Ralf Wernicke
 * @author Dominik Jain
 */
public class WeightedClausalKB implements Iterable<WeightedClause> {
	public enum ConversionMode {
		/**
		 * simply convert the original formula to CNF, no negation is applied
		 */
		NO_NEGATION,
		/**
		 * negate formulas that have negative weights, such that all weights become positive
		 * (which is required for MC-SAT, for example), and use the negated formula's CNF
		 */
		NEGATION_IF_WEIGHT_NEGATIVE,
		/**
		 * negate the original formula if the formula becomes a clause as a result (i.e. if the original formula was
		 * a conjunction of literals), avoiding corresponding splits at conjunctions.
		 * <p>Choose this if the goal is to obtain as many clauses as possible
		 * and avoid the generation of conjunctions at which formulas would need to split (e.g. for MaxWalkSAT).</p>
		 */
		NEGATION_IF_CLAUSE_RESULTS
	}

    protected Vector<WeightedClause> clauses;
    protected HashMap<WeightedClause, Formula> cl2Formula;
    protected edu.tum.cs.util.datastruct.Map2List<WeightedFormula, WeightedClause> formula2clauses;

    /**
     * constructs a weighted clausal KB from a collection of weighted formulas
     * @param kb some collection of weighted formulas
     * @param requirePositiveWeights whether to negate formulas with negative weights to yield positive weights only
     * @throws java.lang.Exception
     */
    public WeightedClausalKB(Iterable<WeightedFormula> kb, boolean requirePositiveWeights) throws Exception {
    	this();
    	ConversionMode conversionMode = requirePositiveWeights ? 
    			ConversionMode.NEGATION_IF_WEIGHT_NEGATIVE : ConversionMode.NO_NEGATION;
        for(WeightedFormula wf : kb) {
            addFormula(wf, conversionMode);
        }
    }
    
    /**
     * constructs a weighted clausal KB from a collection of weighted formulas
     * @param kb some collection of weighted formulas
     * @param requirePositiveWeights whether to negate formulas with negative weights to yield positive weights only
     * @throws java.lang.Exception
     */
    public WeightedClausalKB(Iterable<WeightedFormula> kb, ConversionMode conversionMode) throws Exception {
    	this();
        for(WeightedFormula wf : kb) {
            addFormula(wf, conversionMode);
        }
    }
    
    /**
     * constructs an empty weighted clausal KB
     */
    public WeightedClausalKB() {
        clauses = new Vector<WeightedClause>();
        cl2Formula = new HashMap<WeightedClause, Formula>();
        formula2clauses = new edu.tum.cs.util.datastruct.Map2List<WeightedFormula, WeightedClause>();    	
    }
   
    /**
     * adds an arbitrary formula to the knowledge base (converting it to CNF and splitting it into clauses) 
     * @param wf formula whose clauses to add (it is automatically converted to CNF and split into clauses; the association between the formula and its clauses is retained)
     * @param makeWeightPositive whether to negate the formula if its weight is negative
     * @throws java.lang.Exception
     */
    public void addFormula(WeightedFormula wf, boolean makeWeightPositive) throws Exception {
    	addFormula(wf, makeWeightPositive ? ConversionMode.NEGATION_IF_WEIGHT_NEGATIVE : ConversionMode.NO_NEGATION);
    }
    
    /**
     * Adds a weighted formula with known CNF to this knowledge base
     * @param wf the weighted formula
     * @param cnf the formula's CNF
     * @throws Exception
     */
    protected void addFormula(WeightedFormula wf, Formula cnf) throws Exception {
    	if(cnf instanceof Conjunction) { // conjunction of clauses
            Conjunction c = (Conjunction) cnf;
            int numChildren = c.children.length;
            for(Formula child : c.children) {
            	try {
            		addClause(wf, new WeightedClause(child, wf.weight / numChildren, wf.isHard));
            	}
            	catch(TautologyException e) {}
            }
        } 
        else if(!(cnf instanceof TrueFalse)) { // clause
            try {
            	addClause(wf, new WeightedClause(cnf, wf.weight, wf.isHard));
            }
            catch(TautologyException e) {}
        }
	}

	protected boolean isConjunctionOfLiterals(Formula cnf) {
    	if(cnf instanceof Conjunction) {
            Conjunction c = (Conjunction) cnf;
            for(Formula child : c.children) {
            	if (child instanceof ComplexFormula) {
            		return false;
            	}
            }
            return true;
        }     
    	else {
    		return false;
    	}
    }
    
    /**
     * adds an arbitrary formula to the knowledge base (converting it to CNF and splitting it into clauses) 
     * @param wf formula whose clauses to add (it is automatically converted to CNF and split into clauses; the association between the formula and its clauses is retained)
     * @param conversionMode the mode which controls how the conversion process performs the CNF conversion
     * @throws java.lang.Exception
     */
    public void addFormula(WeightedFormula wf, ConversionMode conversionMode) throws Exception {
    	Formula cnf;
    	switch (conversionMode) {
    	case NO_NEGATION:
    		cnf = wf.formula.toCNF();
    		break;
    	case NEGATION_IF_WEIGHT_NEGATIVE:
    		if(wf.weight < 0) {
        		wf.weight *= -1;
        		wf.formula = new probcog.logic.Negation(wf.formula);
        	}
    		cnf = wf.formula.toCNF();
    		break;
		case NEGATION_IF_CLAUSE_RESULTS:
			cnf = wf.formula.toCNF();
			if (isConjunctionOfLiterals(cnf)) { 
				wf.weight *= -1;
				wf.formula = new probcog.logic.Negation(wf.formula);
				cnf = new probcog.logic.Negation(cnf).toCNF();
			}
			break;
		default:
			throw new RuntimeException("Unhandled conversion mode " + conversionMode);
    	}
    	addFormula(wf, cnf);
    }
    
    /**
     * adds a weighted clause to this KB
     * @param wf the weighted formula whose CNF the clause appears in
     * @param wc the clause to add
     */
    public void addClause(WeightedFormula wf, WeightedClause wc) {
        cl2Formula.put(wc, wf.formula);
        clauses.add(wc);
        formula2clauses.add(wf, wc);
    }
    
    /**
     * adds a weighted clause to the KB (where the weighted formula the clause originated from is the clause itself) 
     * @param wc the weighted clause to add
     */
    public void addClause(WeightedClause wc) {
    	addClause(new WeightedFormula(wc, wc.weight, wc.isHard), wc);
    }

    /**
     * Method returns the iterator of the weighted clauses in knowledge base.
     * @return Iterator of weighted clauses
     */
    public Iterator<WeightedClause> iterator() {
        return clauses.iterator();
    }

    /**
     * returns the number of weighted clauses in the knowledge base.
     * @return size of the knowledge base (number of weighted clauses)
     */
    public int size() {
        return clauses.size();
    }

    /**
     * prints all weighted clauses in the knowledge base to stdout
     */
    public void print() {
        int i = 0;
        for (WeightedClause c : this)
            System.out.printf("%4d  %s\n", ++i, c.toString());
    }

    /**
     * @return a mapping from weighted clauses to the original formulas they were part of. 
     */
    public HashMap<WeightedClause, Formula> getClause2Formula() {
        return cl2Formula;
    }

    /**
     * gets a set of entries with formulas and the clauses that the formulas are made up of when converted to CNF
     * @return
     */
    public Set<Entry<WeightedFormula,Vector<WeightedClause>>> getFormulasAndClauses() {
        return formula2clauses.entrySet();
    }
}
