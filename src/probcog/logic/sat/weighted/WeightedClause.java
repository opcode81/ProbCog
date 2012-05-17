package probcog.logic.sat.weighted;


import probcog.logic.Formula;
import probcog.logic.sat.Clause;


/**
 * represents a weighted clause
 * @author jain
 */
public class WeightedClause extends Clause {

    public double weight;
    /**
     * whether the clause is to be considered a hard constraint
     */
    public boolean isHard;

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
        this.isHard = hard;        
    }

    public String toString() {
        return weight + " " + super.toString();
    }
}

