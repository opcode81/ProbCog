package edu.tum.cs.srl.mln;


import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.logic.ComplexFormula;
import edu.tum.cs.logic.Disjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.IPossibleWorld;
import edu.tum.cs.tools.StringTool;


/**
 * Class of a weighted clause for the MAPWalkSAT algorithm
 * @author wernickr
 */
public class WeightedClause extends ComplexFormula {

    public GroundLiteral[] lits;
    public double weight;
    public boolean hard;

    /**
     * Constructor to instantiate a weighted clause
     * @param f
     * @param weight
     * @param hard
     * @throws java.lang.Exception
     */
    public WeightedClause(Formula f, double weight, boolean hard) throws Exception {
        this.weight = weight;
        this.hard = hard;
        if (f instanceof GroundLiteral) {
            lits = new GroundLiteral[1];
            lits[0] = (GroundLiteral) f;
        } else if (f instanceof Disjunction) {
            Disjunction d = (Disjunction) f;
            lits = new GroundLiteral[d.children.length];
            for (int i = 0; i < lits.length; i++) {
                if (d.children[i] instanceof GroundLiteral)
                    lits[i] = (GroundLiteral) d.children[i];
                else
                    throw new Exception("Disjunction contains child of unacceptable type " + d.children[i].getClass().getSimpleName());
            }
        } else
            throw new Exception("Instance of type " + f.getClass().getSimpleName() + " cannot be treated as a clause");
    }

    /**
     * Method returns the boolean value of the weighted cluase in the given possible world.
     * @param w state (possible world) to be checked
     * @return boolean value, true if weighted clause is true
     */
    @Override
    public boolean isTrue(IPossibleWorld w) {
        for (GroundLiteral lit : lits)
            if (lit.isTrue(w))
                return true;
        return false;
    }

    /**
     * Method is not supported yet.
     * @return Should return the formula in CNF.
     */
    @Override
    public Formula toCNF() {
        return this;
    }

    /**
     * Prints out the weighted clause in a string.
     * @return String of weighted clause
     */
    public String toString() {
        return weight + " " + StringTool.join(" v ", this.lits);
    }

    /**
     * Methodm returns the weight of the clause
     * @return double value of weight
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Method is not supported yet.
     * @param evidence
     * @return Should return formula
     */
   
    @Override
    public Formula simplify(Database evidence) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
