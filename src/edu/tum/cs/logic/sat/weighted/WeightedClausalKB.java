package edu.tum.cs.logic.sat.weighted;

import edu.tum.cs.logic.Conjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.TrueFalse;
import edu.tum.cs.srl.mln.MarkovRandomField;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;

/**
 * A knowledge base of weighted clauses processed by the MAPMaxWalkSAT algorithm
 * @author wernickr
 */
public class WeightedClausalKB implements Iterable<WeightedClause> {

    protected Vector<WeightedClause> clauses;
    private HashMap<Formula, Double> wFormulas;
    private HashMap<WeightedClause, Formula> cl2Formula;
    private edu.tum.cs.tools.Map2List<Formula, WeightedClause> formula2clauses;
    private MarkovRandomField kb;

    /**
     * Constructor to instantiate a knowledge base of weighted clauses
     * @param kb instantiated Markov Random Field
     * @throws java.lang.Exception
     */
    public WeightedClausalKB(MarkovRandomField kb) throws Exception {
        // obtain clausal form
        clauses = new Vector<WeightedClause>();
        wFormulas = new HashMap<Formula, Double>();
        cl2Formula = new HashMap<WeightedClause, Formula>();
        formula2clauses = new edu.tum.cs.tools.Map2List<Formula, WeightedClause>();
        this.kb = kb;
        // keeps track of the formulas and their according weight
        for (Formula f : kb.groundedFormulas.keySet()) {
            addFormula(f, kb.groundedFormulas.get(f));
            wFormulas.put(f, kb.groundedFormulas.get(f));
        }
    }

    /**
     * Method adds a formula into the knowledge base and returns the formula in CNF
     * @param f Formula which should be inserted
     * @param weight according weight (double)
     * @return returns the formula in
     * @throws java.lang.Exception
     */
    public Formula addFormula(Formula f, Double weight) throws Exception {
        f = f.toCNF();
        if (f instanceof Conjunction) {
            Conjunction c = (Conjunction) f;
            for (Formula child : c.children) {
                WeightedClause wc = new WeightedClause(child, weight, weight == kb.mln.getMaxWeight());
                cl2Formula.put(wc, f);
                clauses.add(wc);
                formula2clauses.add(f, wc);                
            }
        } else if (!(f instanceof TrueFalse)) {
                WeightedClause wc = new WeightedClause(f, weight, weight == kb.mln.getMaxWeight());
                cl2Formula.put(wc, f);
                clauses.add(wc);
                formula2clauses.add(f, wc);
            }
        return f;
    }

    /**
     * Method returns the iterator of the weighted clauses in knowledge base.
     * @return Iterator of weighted clauses
     */
    public Iterator<WeightedClause> iterator() {
        return clauses.iterator();
    }

    /**
     * Returns the count of weighted clauses in the knowledge base.
     * @return size of the knowledge base (count of weighted clauses)
     */
    public int size() {
        return clauses.size();
    }

    /**
     * Prints all weighted clauses in the knowledge base on a console
     */
    public void print() {
        int i = 0;
        for (WeightedClause c : this)
            System.out.printf("%4d  %s\n", ++i, c.getWeight() + " " + c.toString());
    }

    /**
     * Method returns a HashMap from formula to their according weight.
     * @return Formulas with their according weight.
     */
    public HashMap<Formula, Double> getWeightedFormulas() {
        return wFormulas;
    }

    /**
     * Method returns a HashMap from weighted clauses to their according formula.
     * @return weighted clauses with their according formula
     */
    public HashMap<WeightedClause, Formula> getClauseFormula() {
        return cl2Formula;
    }

    /**
     * gets the clauses that make up the given formula
     * @return a collection of clauses
     */
    public Collection<WeightedClause> getClauses(Formula f) {
        return formula2clauses.get(f);
    }
}
