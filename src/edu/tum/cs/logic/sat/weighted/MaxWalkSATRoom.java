package edu.tum.cs.logic.sat.weighted;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.tools.Stopwatch;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.PrintWriter;
import java.util.Vector;

/**
 * This class ia an extension of the MAPMaxWalkSAT-class in which some methods are fitted to our special facility management problem.
 * @author wernickr
 */
public class MaxWalkSATRoom extends MaxWalkSAT {

    Dimension screenSize;
    Stopwatch sw;
    File testFile;
    int maxsteps;
    PrintWriter fr;

    /**
     * Constructor to instantiate a MAPMaxWalkSATRoom.
     * @param kb a knowledge base of weighted clauses, which can be instantiated by an Markov Random Field (MRF)
     * @param state a possible world containing all variables of the Markov Logic Network (MLN)
     * @param vars world variables of the MLN which is needed by the class
     * @param evidence the database of the MRF -> given contraints and true/false values of the variables
     * @param threshold maximum steps taken by the algorithm (a possible integer value)
     * @param sw an externally instantiated and started Instance of type stopwatch
     * @throws java.lang.Exception
     */
    public MaxWalkSATRoom(WeightedClausalKB kb, PossibleWorld state, WorldVariables vars, Database evidence, double threshold, Stopwatch sw) throws Exception {
        super(kb, state, vars, evidence, threshold);
        this.sw = sw;
        // needed for our tool to display the solutions
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    // needed for the evaluation to save the solutions in a file
        /*
    int x = 0;
    do{
    testFile = new File("D:/results/testResult_" + x++ + ".txt");
    }while (testFile.exists());
    fr = new PrintWriter(testFile);*/

    }

    /**
     * Method returns the file inj which the solutions are stored.
     * @return File with solution.
     */
    public File getTestFile() {
        return testFile;
    }

    /**
     * Sets the initial state of the possible world so that the algorithm starts the search at the same positions the staffmembers are sitting before. (nachher = vorher)
     */
    @Override
    public void setState() {
        //sets the state equal to evidence (nachher = vorher)
        for (int i = 0; i < vars.size(); i++) {
            if (this.evidence.containsKey(i)) continue;
            //if the groundAtom contains "nachher", set the state to the one of groundAtom "vorher"
            if (vars.get(i).toString().contains("Nachher")) {
                GroundAtom ga = vars.get(vars.get(i).toString().replace("Nachher", "Vorher"));
                state.set(i, ga.isTrue(state));
            } else {
                //if groundAtom is not set as "vorher" then set to random either one of the block or undependend
                Block block = vars.getBlock(i);
                if (block != null) {
                    int j = rand.nextInt(block.size());
                    for (int k = 0; k < block.size(); k++) {
                        boolean value = k == j;
                        state.set(i + k, value);
                    }
                    i += block.size();
                } else {
                    state.set(i, rand.nextBoolean());
                }
            }
        }
        // Afterwards test whether all blocks are set, if not set random ground atom true
        for (int i = 0; i < vars.size(); i++){
            Block block = vars.getBlock(i);
            if (block != null){
                if(block.getTrueOne(state) == null){
                    int j = rand.nextInt(block.size());
                    for (int k = 0; k < block.size(); k++){
                        boolean value = k == j;
                        state.set(i + k, value);
                    }
                    i += block.size();
                }
            }
        }
    }

    // this method is only needed by our facility management tool to show the solution
    /*public void showSolution() {
    Solution sol = new Solution(null, true, state, ev, vars);
    sol.setLocation((int) (screenSize.getWidth() - sol.getSize().getWidth()) / 2, (int) (screenSize.getHeight() - sol.getSize().getHeight()) / 2);
    sol.show();
    }*/
    /**
     * With this method one can set the method the daltacosts of a goriundatom are calculated. Possible values are:
     * 1 - Calculation of deltacosts: Always 1/Count of constraints of the according formula
     * 2 - Calculation of deltacosts: Only if value of formula was changed (then complete weight of the formula are the deltacosts of the groundatom)
     * 3 - see 2, if no change of the value of the according formula was made then see 1
     * @param deltaCostCalcMethod 1, 2 or 3 (see above for details)
     */
    public void setDeltaCostCalcMethod(int deltaCostCalcMethod) {
        this.deltaCostCalcMethod = deltaCostCalcMethod;
    }

    public static void main(String[] args) throws Exception {

        String mlnfile = "C:/raumplanung - neu.mln";
        String dbfile = "C:/test-12.db";
        MarkovLogicNetwork mln = new MarkovLogicNetwork(mlnfile, true, null);
        // read evidence + ground model
        MarkovRandomField mrf = mln.groundMLN(dbfile);
        // run algorithm
        PossibleWorld state = new PossibleWorld(mrf.getWorldVariables());
        //state.print();
        WeightedClausalKB wckb = new WeightedClausalKB(mrf);
        Stopwatch sw = new Stopwatch();
        sw.start();
        MaxWalkSATRoom ss = new MaxWalkSATRoom(wckb, state, mrf.getWorldVariables(), mrf.getDb(), mln.getMaxWeight(), sw);
        ss.setMaxsteps(10000);
        ss.run();
        sw.stop();
        //System.out.println("Sum Weights: " + mln.getMaxWeight());
        System.out.println("********** Solution found: **********");
        System.out.println("Steps: " + ss.getStep());
        state.print();
        /*System.out.println("SECOND RUN");
        ss.run();
        System.out.println("done");
        state.print();
         * */
        System.out.println("time taken: " + sw.getElapsedTimeSecs());
        //ss.showSolution();
        ss.printBestState();
    }

    /**
     * With that method one can set the maximum number of steps taken by the algorith to find a solution.
     * @param maxsteps Integer value of maximum number of steps taken by the algorithm
     */
    public void setMaxsteps(int maxsteps) {
        this.maxsteps = maxsteps;
    }

    /**
     * Method starts and controls the activities of the algorithm. In this case there will be executed a walkSatMove (greedyMove). if there is found a constraint which flip enhances the state it will be fliped (see walkSatMove for details), else a SAMove (random flip) will be executed.
     */
    @Override
    public void run() {
        // init
        bottlenecks.clear();
        unsatisfiedConstraints.clear();
        setState();
        minSum = Integer.MAX_VALUE;
        step = 1;
        lastMinStep = 0;
        satmoves = 0;
        greedymoves = 0;
        double diffSum = 0;
        int minSteps = 0;

        // initialize trueOnes and bottlenecks of constraints
        for (Constraint c : cl2Formula.keySet()) {
            c.initState();
        }

        // initialize mapping of formulas to containing satisfied clauses
        initFormulaState();

        // initial calculation of the unsatisfied sum of weights
        unsatisfiedSum();

        while (step < maxsteps) {
            // calculation of the difference between actually found (unsSum) and globally found minimal unsatisfied sum (minSum) -> acually unused
            diffSum = unsSum - minSum;
            // if there is another new minimal unsatisfied value
            if (unsSum <= minSum) {
                if (unsSum < minSum) {
                    // saves new minimum, resets the steps which shows how often the algorithm hits the minimum (minSteps)
                    minSum = unsSum;
                    minSteps = 0;
                    // optional: saves actual best state
                    bestState = state.clone();
                    // count of step in which the new minimum was found is saved (lastMinStep)
                    lastMinStep = step;
                }
                // if the actual minimum is hit again minSteps where set +1
                if (unsSum == minSum) {
                    minSteps++;
                }
            }

            // print state of progress everey xx steps (e.g. 500)
            if (step % 500 == 0) {
                System.out.println("MAPWaxWalkSAT step " + step + ", " + countUnsCon / unsatisfiedConstraints.size() * 100 + " % of hard constraints unsatisfied, sum: " + unsSum + ", minsteps: " + minSteps + ", minSum: " + minSum /*+ "diff: " + diffSum + " , " + (Math.pow(Math.E, ((double) (-diffSum) / ((new Double(step) / new Double(vars.size()))))))*/ +
                        " Time: " + sw.getElapsedTimeSecs() + ", Satmoves: " + satmoves + ", Greedymoves: " + greedymoves + ", LastMinStep: " + lastMinStep);
            }
            // Executes walkSatMove (see there -> override in this class)
            walkSATMove();
            step++;
        }
    }

    /**
     * Prints all solutions and necessary informations into a file set at the constructor.
     */
    public void printBestState() {
        System.out.println("Methode: " + deltaCostCalcMethod);
        System.out.println("Steps: " + maxsteps);
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

    /**
     * Jumps to randomly chosen position in the list of unsatisfied constraints. Tries incremental to find an actually unsatisfied constraint which enhances the state of the "world".
     * If no one is found the algorithm executes a SAMove (random flip) with a possibility of 100% (optional value possible, see lines 240 - 243)
     */
    @Override
    protected void walkSATMove() {
        boolean found;
        double formulaDelta = 0;
        // a random value in size of list of unsatisfied constraints (= random position)
        int x = rand.nextInt(unsatisfiedConstraints.size());
        Formula parent;

        do {
            // if the end of the list is reached execute a SAMove
            if (x == unsatisfiedConstraints.size()) {
//                if (rand.nextDouble() > 0.95) {
                satmoves++;
                SAMove();
//                }
                return;
            } else {
                // else search for an unsatisfied constrint to be satisfied
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
                        greedymoves++;
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
