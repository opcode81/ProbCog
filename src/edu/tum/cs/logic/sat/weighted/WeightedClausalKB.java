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
    protected edu.tum.cs.tools.Map2List<WeightedFormula, WeightedClause> formula2clauses;

    /**
     * Constructor to instantiate a knowledge base of weighted clauses
     * @param kb instantiated Markov Random Field
     * @throws java.lang.Exception
     */
    public WeightedClausalKB(Iterable<WeightedFormula> kb) throws Exception {
        clauses = new Vector<WeightedClause>();
        cl2Formula = new HashMap<WeightedClause, Formula>();
        formula2clauses = new edu.tum.cs.tools.Map2List<WeightedFormula, WeightedClause>();
        for(WeightedFormula wf : kb) {
            addFormula(wf);
        }
    }
   
    /**
     * adds an arbitrary formula to the knowledge base (converting it to CNF and splitting it into clauses) 
     * @param wf formula whose clauses to add (it is automatically converted to CNF and split into clauses; the association between the formula and its clauses is retained)
     * @throws java.lang.Exception
     */
    public void addFormula(WeightedFormula wf) throws Exception {
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
            System.out.printf("%4d  %s\n", ++i, c.getWeight() + " " + c.toString());
    }

    /**
     * @return a mapping from weighted clauses to the original formulas they were part of. 
     */
    public HashMap<WeightedClause, Formula> getClause2Formula() {
        return cl2Formula;
    }

    /**
     * gets a set of entries with formulas and the clauses that the formulas are made up of when converted to CNF
     * @return a collection of clauses
     */
    public Set<Entry<WeightedFormula,Vector<WeightedClause>>> getFormulasAndClauses() {
        return formula2clauses.entrySet();
    }
}
