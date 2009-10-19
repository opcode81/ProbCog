package edu.tum.cs.logic.sat.weighted;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import edu.tum.cs.logic.Conjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.TrueFalse;

/**
 * A knowledge base of weighted clauses that is built up from general weighted formulas (retaining a mapping from formulas to clauses and vice versa) 
 * @author wernickr, jain
 */
public class WeightedClausalKB implements Iterable<WeightedClause> {

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
        for(WeightedFormula wf : kb) {
            addFormula(wf, requirePositiveWeights);
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
    	if(makeWeightPositive && wf.weight < 0) {
    		wf.weight *= -1;
    		wf.formula = new edu.tum.cs.logic.Negation(wf.formula);
    	}
    	// convert formula to CNF
        Formula cnf = wf.formula.toCNF();
        // add its clauses
        if(cnf instanceof Conjunction) { // conjunction of clauses
            Conjunction c = (Conjunction) cnf;
            int numChildren = c.children.length;
            for(Formula child : c.children)
                addClause(wf, new WeightedClause(child, wf.weight / numChildren, wf.isHard));
        } 
        else if(!(cnf instanceof TrueFalse)) { // clause
            addClause(wf, new WeightedClause(cnf, wf.weight, wf.isHard));
        }
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
            System.out.printf("%4d  %s\n", ++i, c.weight + " " + c.toString());
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
