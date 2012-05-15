/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.tum.cs.srl.mln;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Vector;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.Formula.FormulaSimplification;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.IPossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.Variable;

/**
 * Class that represents a grounded instance of a MLN-file
 * @author wernickr, jain
 */
public class MarkovRandomField implements Iterable<WeightedFormula> {
    protected Database db;
    public MarkovLogicNetwork mln;
    protected Vector<WeightedFormula> weightedFormulas;
    protected WorldVariables vars;
    /**
     * whether to simplify grounded formulas based on evidence
     */
    protected final boolean simplifyGroundedFormulas = true;
    
    /**
     * @param mln a Markov logic network
     * @param db an evidence database containing the set of objects for which to ground the MLN  
     * @param storeFormula whether to store the grounded formulas that are generated
     * @param gc an optional callback (which is called for each grounded formula), may be null
     * @throws Exception 
     * @throws Exception 
     */
    public MarkovRandomField(MarkovLogicNetwork mln, Database db, boolean storeFormulas, GroundingCallback gc) throws Exception {
        this.db = db;
        this.vars = new WorldVariables();
        this.mln = mln;        
        groundVariables();
        groundFormulas(storeFormulas, gc);
    }
    
    public MarkovRandomField(MarkovLogicNetwork mln, Database db) throws Exception {
    	this(mln, db, true, null);
    }
    
    /**
     * Method that returns worldVariables of the given MLN
     * @return
     */
    public WorldVariables getWorldVariables() {
        return vars;
    }
    
    /**
     * creates the set ground atoms, considering functional predicates (and extending evidence as needed) 
     * @throws Exception 
     */
    protected void groundVariables() throws Exception {
    	for(Signature sig : mln.getSignatures()) {
    		groundVariables(sig, new String[sig.argTypes.length], 0, mln.getFunctionallyDeterminedArgument(sig.functionName));
    	}
    }
    
    protected void groundVariables(Signature sig, String[] args, int i, Integer functionallyDeterminedArg) throws Exception {
    	if(i == args.length) {
    		if(functionallyDeterminedArg != null) {
    			// build the block of variables and check if we have positive evidence for one of them
    			Vector<GroundAtom> block = new Vector<GroundAtom>();
        		Iterable<String> dom = db.getDomain(sig.argTypes[functionallyDeterminedArg]);
        		if(dom == null)
        			throw new Exception("Domain " + sig.argTypes[functionallyDeterminedArg] + " not in database");
        		GroundAtom trueOne = null;
        		for(String value : dom) {
        			args[functionallyDeterminedArg] = value;
        			GroundAtom ga = new GroundAtom(sig.functionName, args.clone()); 
        			block.add(ga);
        			Variable var = db.getVariable(ga.toString());
        			if(var != null && var.isTrue()) {
        				if(trueOne != null)
        					throw new Exception(String.format("The block the variable '%s' is in contains more than one true ground atom", ga.toString()));
        				trueOne = ga; 
        			}
        		}
        		// if we have positive evidence, explicitly set the others to false in the database (to make full use of the evidence when grounding the formulas later on)
        		if(trueOne != null) {
        			for(GroundAtom ga : block)
        				if(ga != trueOne && !db.contains(ga.toString()))
        					db.addVariable(new Variable(ga.predicate, ga.args, "False", mln));
        		}
        		// add the block to the set of vars
    			vars.addBlock(block);
    			//System.out.println("Block: " + block);
    		}
    		else {
    			vars.add(new GroundAtom(sig.functionName, args.clone()));
    		}
    		return;
    	}    	
    	if(functionallyDeterminedArg != null && functionallyDeterminedArg.equals(i)) { // skip the functionally determined argument
    		groundVariables(sig, args, i+1, functionallyDeterminedArg);    		
    	}
    	else {
    		Iterable<String> dom = db.getDomain(sig.argTypes[i]);
    		if(dom == null)
    			throw new Exception("Domain '" + sig.argTypes[i] + "' not found in the database");
    		for(String value : dom) {
    			args[i] = value;
    			groundVariables(sig, args, i+1, functionallyDeterminedArg);
    		}
    	}
    }
    
    /**
     * creates groundings for all formulas
     * @param makelist boolean (if true the grounded formula will be saved in a set)
     * @param gc callback method (if not null, the callback method is called for each grounded formula)
     * @throws Exception 
     */
    protected void groundFormulas(boolean makelist, GroundingCallback gc) throws Exception {
        weightedFormulas = new Vector<WeightedFormula>();
        for(WeightedFormula wf : mln.getFormulas()) {
        	double weight = wf.weight;
        	boolean isHard = wf.isHard;
        	FormulaSimplification simplification = simplifyGroundedFormulas ? (isHard ? FormulaSimplification.OnDisallowFalse : FormulaSimplification.On) : FormulaSimplification.None;
        	Vector<Formula> groundings;
        	try {
        		groundings = wf.formula.getAllGroundings(db, vars, simplification);
        	}
        	catch(Exception e) {
        		throw new Exception("Error while grounding formula '" + wf.formula.toString() + "'", e);
        	}
        	//System.out.printf("%d groundings of formula %s\n", groundings.size(), form.toString());
            for(Formula gf : groundings) {            	
            	WeightedFormula gwf = new WeightedFormula(gf, weight, isHard);
                if(makelist)
                    weightedFormulas.add(gwf);
                if(gc != null)
                    gc.onGroundedFormula(gwf, this);
            }
        }
    }
    
    /**
     * returns the database with which this MRF was grounded
     * @return
     */
    public Database getDb() {
        return db;
    }

	public Iterator<WeightedFormula> iterator() {
		return weightedFormulas.iterator();
	}
	
	public void print(PrintStream out) {
		for(WeightedFormula wf : this)
			out.println(wf.toString());
	}
	
	/**
	 * gets the sum of weights of formulas satisfied in the given possible world
	 * @return
	 */
	public double getWorldValue(IPossibleWorld w) {
		double s = 0;
		for(WeightedFormula wf : this)
			if(wf.formula.isTrue(w))
				s += wf.weight;
		return s;
	}
	
	public double getSumOfUnsatClauseWeights(IPossibleWorld w) {
		double s = 0;
		for(WeightedFormula wf : this) {
			if(!wf.formula.isTrue(w)) {
				s += wf.weight;
				//System.out.println("UNSAT: " + wf);
			}
			else 
				;//System.out.printf("%s\n", wf);
		}
		return s;
	}
	
	public int getNumFormulas() {
		return this.weightedFormulas.size();
	}
}