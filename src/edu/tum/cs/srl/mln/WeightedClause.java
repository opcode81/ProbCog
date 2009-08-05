package edu.tum.cs.srl.mln;


import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.sat.Clause;


/**
 * Class of a weighted clause for the MAPWalkSAT algorithm
 * @author wernickr, jain
 */
public class WeightedClause extends Clause {

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
    	super(f);
        this.weight = weight;
        this.hard = hard;        
    }

    /**
     * Prints out the weighted clause in a string.
     * @return String of weighted clause
     */
    public String toString() {
        return weight + " " + super.toString();
    }

    /**
     * Method returns the weight of the clause
     * @return double value of weight
     */
    public double getWeight() {
        return weight;
    }
}

