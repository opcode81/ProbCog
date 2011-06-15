package edu.tum.cs.logic.sat.weighted;

import java.util.Vector;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.srl.Database;

/**
 * This class ia an extension of the MAPMaxWalkSAT-class in which some methods are fitted to our special facility management problem.
 * @author wernickr
 */
public class MaxWalkSATRoom extends MaxWalkSATEx {
	
	/**
	 * whether to use an alternative method that does not actually use the p parameter (Ralf's method)
	 */
	public boolean alternativeMethod = true;

    /**
     * Constructor to instantiate a MAPMaxWalkSATRoom.
     * @param kb a knowledge base of weighted clauses, which can be instantiated by an Markov Random Field (MRF)
     * @param state a possible world containing all variables of the Markov Logic Network (MLN)
     * @param vars world variables of the MLN which is needed by the class
     * @param evidence the database of the MRF -> given contraints and true/false values of the variables
     * @param sw an externally instantiated and started Instance of type stopwatch
     * @throws java.lang.Exception
     */
    public MaxWalkSATRoom(WeightedClausalKB kb, PossibleWorld state, WorldVariables vars, Database evidence) throws Exception {
        super(kb, state, vars, evidence);
    }

    /**
     * Sets the initial state of the possible world so that the algorithm starts the search at the same positions the staffmembers are sitting before. (nachher = vorher)
     */
    @Override
    public void setState() {
    	// set state randomly
    	super.setState();
        // now reset the workplace after relocation to the same as before
    	int count = 0;
        for(int i = 0; i < vars.size(); i++) {
            if(this.evidence.containsKey(i)) 
            	continue;
            GroundAtom ga = vars.get(i);
            String gaName = ga.toString();
            GroundAtom vorher;
            if(gaName.startsWith("workplaceAfter"))
                vorher = vars.get(gaName.replace("After", "Before"));
        	else if(gaName.startsWith("employeeIn")) 
                vorher = vars.get(gaName.replace("employeeIn", "roomBefore"));
            else
            	continue;
            state.set(i, vorher.isTrue(state));
            count++;
        }
        System.out.printf("reinitialized %d ground atoms according to room planning heuristic.\n", count);
    }

    /**
     * Prints all solutions and necessary informations into a file set at the constructor.
     */
    public void printBestState() {
        System.out.println("Methode: " + deltaCostCalcMethod);
        System.out.println("Steps: " + this.maxSteps);
        System.out.println("*********** Best State after " + lastMinStep + " Steps *************");
        boolean[] s = bestState.getState();
        for (int c = 0; c < s.length; c++) {
            String temp = "";
            if (!s[c]) {
                temp = "!";
            }
            System.out.println(temp += vars.get(c).toString());
            System.out.println(vars.get(c).toString() + " - > " + s[c]);
        }
        System.out.println("Unsatisfied Sum: " + minSum);
        //fr.close();
    }
    
    protected void randomMove() {
    	if(alternativeMethod)
    		greedyMove();
    	else
    		super.randomMove();
    }
    
    /**
     * Jumps to randomly chosen position in the list of unsatisfied constraints. Tries incremental to find an actually unsatisfied constraint which enhances the state of the "world".
     * If no one is found the algorithm executes a SAMove (random flip) with a possibility of 100% (optional value possible, see lines 240 - 243)
     */
    @Override
    protected void greedyMove() {
    	if(!alternativeMethod) {
    		super.greedyMove();
    		return;
    	}
        boolean found;
        double formulaDelta = 0;
        // a random value in size of list of unsatisfied constraints (= random position)
        int x = rand.nextInt(unsatisfiedConstraints.size());
        Formula parent;

        do {
            // if the end of the list is reached execute a SAMove
            if (x == unsatisfiedConstraints.size()) {
                SAMoves++;
                super.randomMove();
                return;
            } else {
                // else search for an unsatisfied constraint to be satisfied
                Vector<Object> bestGAinFormula = new Vector<Object>();
                formulaDelta = 0;
                // Get the according formula of the determined constraint
                parent = cl2Formula.get(unsatisfiedConstraints.get(x));
                do {
                    x++;
                    // for each in the formula containing weighted clause get the candidates and value of the effects on the possible world
                    for (WeightedClause con : formula2clauses.get(parent)) {
                        if (unsatisfiedConstraints.contains(con)) {
                            bestGAinFormula.addAll(con.greedySatisfy());
                        }
                    }
                    // add all effects (values)
                    for (Object o : bestGAinFormula) {
                        if (o instanceof Double) {
                            formulaDelta += ((Double) o).doubleValue();
                        }
                    }
                    // if the satisfaction of the formula enhances the actual state the flip groundatoms
                    if (formulaDelta < 0) {
                        for (Object o : bestGAinFormula) {
                            if (o instanceof GroundAtom) {
                                flipGndAtom((GroundAtom) o);
                            }
                        }
                        found = true;
                        greedyMoves++;
                        break;
                    } else {
                        // else do nothing and determine next constraint
                        found = false;
                        break;
                    }

                } while (!parent.isTrue(state));
            }
        } while (!found);
    }

}
