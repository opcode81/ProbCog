/*******************************************************************************
 * Copyright (C) 2012 Gregor Wylezich, Dominik Jain and Paul Maier.
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
package probcog.wcsp;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.Vector;

import probcog.exception.ProbCogException;
import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.logging.PrintLogger;
import probcog.logging.VerbosePrinter;
import probcog.logic.ComplexFormula;
import probcog.logic.Conjunction;
import probcog.logic.Disjunction;
import probcog.logic.Formula;
import probcog.logic.GroundAtom;
import probcog.logic.GroundLiteral;
import probcog.logic.IPossibleWorld;
import probcog.logic.Negation;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.logic.WorldVariables.Block;
import probcog.logic.sat.weighted.WeightedFormula;
import probcog.srl.BooleanDomain;
import probcog.srl.Database;
import probcog.srl.Signature;
import probcog.srl.mln.MarkovLogicNetwork;
import probcog.srl.mln.MarkovRandomField;
import probcog.wcsp.Constraint.ArrayKey;
import probcog.wcsp.Constraint.Tuple;

import edu.tum.cs.util.StringTool;

/**
 * Converts an instantiated MLN (i.e. a ground MRF) into the Toulbar2 WCSP format
 * @author Gregor Wylezich 
 * @author Dominik Jain
 * @author Paul Maier
 */
public class WCSPConverter implements IParameterHandler, VerbosePrinter {

	protected MarkovLogicNetwork mln;
	protected MarkovRandomField mrf;
	protected PossibleWorld world;
	protected Double divisor;
	protected HashMap<String, HashSet<String>> doms;
	protected HashMap<Integer, Integer> gndID_BlockID;
	/**
	 * list of WCSP variable names
	 */
	protected ArrayList<String> vars;
	/**
	 * maps a ground atom index to a WCSP variable index
	 */
	protected HashMap<Integer, Integer> gndAtomIdx2varIdx;
    /**
     * maps WCSP variable indices to sets of ground atoms encompassed by the variable
     */
	protected HashMap<Integer, Vector<GroundAtom>> varIdx2groundAtoms;
	protected HashMap<String, String> func_dom;
	protected HashMap<Formula, Long> wcspConstraints = new HashMap<Formula, Long>();
	protected PrintStream ps;
	protected long hardCost = -1;
	protected boolean debug = false, verbose = false;
	protected Database db;
	protected boolean cacheConstraints = false;
	protected ParameterHandler paramHandler;
	protected PrintLogger log;
	
    /**
     * @param mrf
     * @throws ProbCogException 
     */
    public WCSPConverter(MarkovRandomField mrf) throws ProbCogException {
    	this.log = new PrintLogger(this);
        this.mln = mrf.mln;
        this.mrf = mrf;    
        this.paramHandler = new ParameterHandler(this);
        paramHandler.add("verbose", "setVerbose");
        paramHandler.add("debug", "setDebug");
        paramHandler.add("wcspWeightScalingFactor", Double.class, f -> this.divisor = 1.0/f,
        		"the scaling factor with which to multiply weights for the integer cost conversion");
    }
    
    public void setCacheConstraints(boolean cache) {
    	this.cacheConstraints = cache;
    }
    
    public void setVerbose(boolean verbose) {
    	this.verbose = verbose;
    }
    
    public void setDebug(boolean debug) {
    	this.debug = debug;
    }
    
    /**
     * computes the divisor that is used to convert MLN weights to WCSP costs
     * @return
     */
    protected double computeDivisor() {
        // get minimum absolute weight and build sorted set of absolute weights
    	TreeSet<Double> weight = new TreeSet<Double>();
        double minAbsWeight = Double.MAX_VALUE;
        for(WeightedFormula wf : mln.getFormulas()) {
        	double absWeight = Math.abs(wf.weight);
            weight.add(absWeight);
            if(absWeight < minAbsWeight && absWeight != 0)
            	minAbsWeight = absWeight;
        }       
        
        // calculate the smallest difference between consecutive weights
        double deltaMin = Double.MAX_VALUE;
        Iterator<Double> iter = weight.iterator();        
        Double w1 = iter.next();
        while(iter.hasNext()) {
            Double w2 = iter.next();
            double diff = w2 - w1;
            if(diff < deltaMin)
            	deltaMin = diff;
            w1 = w2;
        }
        
        double divisor = 1.0;
        if(minAbsWeight < 1.0)
        	divisor *= minAbsWeight;
        if(deltaMin < 1.0)
        	divisor *= deltaMin;
        
        return divisor;
    }

    
    /**
     * performs the conversion of the ground MRF to the WCSP file
     * @param wcspFilename
     * @throws ProbCogException 
     */
    public WCSP run() throws ProbCogException {
    	initialize();        

    	// instantiate WCSP
        int[] domSizes = new int[vars.size()];
        for(int i = 0; i < vars.size(); i++) {
        	HashSet<String> domSet = doms.get(func_dom.get(vars.get(i))); 
            domSizes[i] = domSet == null ? 2 : domSet.size();
        }
        long top = hardCost;   	
    	WCSP wcsp = new WCSP(domSizes, top);
    	
    	// generate evidence constraints
    	log.info("Generating evidence constraints...");
        generateEvidenceConstraints(wcsp);
        
        // generate constraints for weighted formulas, merging constraints with the same domains
        log.info("Generating constraints for %d weighted formulas...", mrf.getNumFormulas());
        HashMap<ArrayKey, Constraint> collectedConstraints = new HashMap<ArrayKey, Constraint>();
        for(WeightedFormula wf : mrf) {
        	Constraint c = generateConstraint(wf);
        	if(c != null) {
        		// check if we have a previous constraint with the same domain
        		ArrayKey key = new ArrayKey(c.getVarIndices());
        		Constraint prevConstraint = collectedConstraints.get(key);
        		if(prevConstraint != null)
        			prevConstraint.merge(c);
        		else {
        			collectedConstraints.put(key, c);
        			wcsp.addConstraint(c);
        		}
        	}
        }
        
       log.info("Constructed %d constraints in total", wcsp.size());
        
        return wcsp;
    }

    /**
     * this method generates a variable for each ground atom; for blocks, only one variable is created
     */
    protected void createVariables() {
        vars = new ArrayList<String>(); // list of new variables
        gndAtomIdx2varIdx = new HashMap<Integer, Integer>(); // maps ground atom indices to WCSP variable indices
        func_dom = new HashMap<String, String>(); // maps variable to domain the variable uses
        varIdx2groundAtoms = new HashMap<Integer, Vector<GroundAtom>>(); // maps a variable index to all ground atoms that are set by this variable
        HashSet<Block> handledBlocks = new HashSet<Block>();

        WorldVariables ww = world.getVariables();
        for(int i = 0; i < ww.size(); i++) {
        	GroundAtom ga = ww.get(i);
            // check whether ground atom is in a block
        	Block block = ww.getBlock(ga.index);
            if(block != null) {
            	if(handledBlocks.contains(block))
            		continue;
            	handledBlocks.add(block);
                
                // generate the new variable name
                StringBuffer shortened = new StringBuffer(ga.predicate);
                int funcArgIdx = mln.getFunctionallyDeterminedArgument(ga.predicate);
                shortened.append('(');
                int k = 0;
                for(int j = 0; j < ga.args.length; j++) {
                	if(j == funcArgIdx)
                		continue;
                    if(k++ > 0)
                        shortened.append(',');
                    shortened.append(ga.args[j]);
                }
                shortened.append(')');
                
                String varName = shortened.toString();                
                int varIdx = vars.size();
                //System.out.printf("adding WCSP block variable %s\n", varName);
                vars.add(varName);                
                Signature sig = mln.getSignature(ga.predicate);
                func_dom.put(varName, sig.argTypes[funcArgIdx]);
                Vector<GroundAtom> tmp = new Vector<GroundAtom>();
                for(GroundAtom gndAtom : block) {
                	gndAtomIdx2varIdx.put(gndAtom.index, varIdx);
                	tmp.add(gndAtom);
                }                
                varIdx2groundAtoms.put(varIdx, tmp);
            }
            else { // it's a boolean variable
            	String varName = ga.toString();
            	int varIdx = vars.size();
            	//System.out.printf("adding WCSP variable %s\n", varName);
                vars.add(varName);                
                gndAtomIdx2varIdx.put(ga.index, varIdx);
                // in this case, the mapping of this variable is set to "boolean" domain
                func_dom.put(varName, "boolean");
                // in this case, the HashSet of Groundatoms only contains the selected Worldvariable
                Vector<GroundAtom> tmp = new Vector<GroundAtom>();
                tmp.add(ga);
                varIdx2groundAtoms.put(varIdx, tmp);
            }
        }
        
        if(log.isDebugEnabled()) {
        	log.debug("WCSP variables:");
        	for(Entry<Integer,Vector<GroundAtom>> e : varIdx2groundAtoms.entrySet()) {
        		log.debug("%s %s", e.getKey(), StringTool.join(", ", e.getValue()));
        	}
        }
    }

    /**
     * this method simplifies the generated variables (if a variable is given by the evidence, it's not necessary for the WCSP) 
     * @param db evidence database
     * @throws ProbCogException 
     */
    protected void simplifyVars(Database db) throws ProbCogException {
    	ArrayList<String> simplifiedVars = new ArrayList<String>(); // list of simplified variables
    	HashMap<Integer, Integer> sf_gndAtomIdx2varIdx = new HashMap<Integer, Integer>(); // mapping of ground atom indices to simplified variable indices
    	HashMap<Integer, Vector<GroundAtom>> sf_varIdx2groundAtoms = new HashMap<Integer, Vector<GroundAtom>>(); // mapping of simplified variable to ground atom
        
        // check all variables for an evidence-entry
        for (int i = 0; i < vars.size(); i++) {
            
            // check all entries in HashSet of the selected variable for an entry in evidence
        	int evidenceAtoms = 0;
            Vector<GroundAtom> gndAtoms = varIdx2groundAtoms.get(i);
            for(GroundAtom g : gndAtoms) {
		           if (db.getVariableValue(g.toString(), false) != null) // evidence entry exists
		                evidenceAtoms++;
            }
            
            // if hashsets (givenAtoms and hashset of the variable) have same size, then all ground atoms are set by the evidence
            // we don't need to handle this variable anymore
            // if hashsets don't have same size, the variable must be handled
            if ((gndAtoms.size() != evidenceAtoms)) {
                // add variable to simplifiedVars
            	int idx = simplifiedVars.size();
                simplifiedVars.add(vars.get(i));
                // save mapping of ground atoms to the new simplified variable
                for (GroundAtom g : varIdx2groundAtoms.get(i))
                    sf_gndAtomIdx2varIdx.put(g.index, idx);
                // clone hashset and save it in the mapping of simplified variables to groundatoms 
                sf_varIdx2groundAtoms.put(idx, gndAtoms);
            }
        }
        
        log.info("Simplification: reduced %d to %d variables", vars.size(), simplifiedVars.size());
        this.vars = simplifiedVars;
        this.gndAtomIdx2varIdx = sf_gndAtomIdx2varIdx;
        this.varIdx2groundAtoms = sf_varIdx2groundAtoms; 
    }

  
    /**
     * this method generates a WCSP Constraint for a weighted formula
     * @param wf the weighted formula
     * @throws ProbCogException 
     */
    protected Constraint generateConstraint(WeightedFormula wf) throws ProbCogException {
        // if the weight is negative, negate the formula and its weight
    	Formula f = wf.formula;
    	double weight = wf.weight;
        if(weight < 0) {
        	f = new Negation(f);
        	weight *= -1;
        } 
        // convert to negation normal form so we get many flat conjunctions or disjunctions, which can be efficiently converted
        f = f.toNNF(); 
    	
        // get all ground atoms of the formula
        HashSet<GroundAtom> gndAtoms = new HashSet<GroundAtom>();
        f.getGroundAtoms(gndAtoms);

        // get corresponding list of WCSP variables
        HashSet<Integer> setVarIndices = new HashSet<Integer>(gndAtoms.size());
        for(GroundAtom g : gndAtoms) {
            // add simplified variable only if the array doesn't contain this sf_variable already
        	Integer idx = gndAtomIdx2varIdx.get(g.index);
        	if(idx == null)
        		throw new ProbCogException("Variable index for '" + g + "' is null");
        	setVarIndices.add(idx);
        }
        int[] referencedVarIndices = new int[setVarIndices.size()];
        int i = 0;
        for(Integer varIdx : setVarIndices)
        	referencedVarIndices[i++] = varIdx;
        Arrays.sort(referencedVarIndices); // have the array sorted to simplify constraint unification

        // get cost value for this constraint (which will apply if the formula is false)
        long cost;
        if(wf.isHard)
        	cost = hardCost;
        else
        	cost = Math.round(weight / divisor);
        
        ArrayList<Tuple> relevantSettings = null;
        long defaultCosts = -1;
        
        // try the simplified conversion method 
        boolean generateAllPossibilities = true;
        boolean isConjunction = f instanceof Conjunction; 
        if(isConjunction || f instanceof Disjunction) {
        	generateAllPossibilities = false;
        	try {
        		relevantSettings = new ArrayList<Tuple>();
        		this.gatherConstraintTuplesSimplified((ComplexFormula)f, referencedVarIndices, cost, relevantSettings, isConjunction);
        		defaultCosts = isConjunction ? cost : 0;
        	}
        	catch(SimplifiedConversionNotSupportedException e) {
        		log.debug("No simplified conversion (%s): %s", e.getMessage(), f.toString());
        		generateAllPossibilities = true;
        	}
        }
        
        // if necessary, use the complex conversion method which looks at all possible settings
        if(generateAllPossibilities) {        
	        // generate all possibilities for this constraint
	        ArrayList<Tuple> settingsZero = new ArrayList<Tuple>();
	        ArrayList<Tuple> settingsOther = new ArrayList<Tuple>();
	        gatherConstraintTuples(f, referencedVarIndices, 0, world, new int[referencedVarIndices.length], cost, settingsZero, settingsOther);                 
	        
	        if(settingsOther.size() < settingsZero.size()) { // in this case there are more null-values than lines with a value differing from 0
	        	relevantSettings = settingsOther;
	            // the default costs (0) are calculated and set in the first line of the constraint
	            defaultCosts = 0;
	        } 
	        else { // there are fewer settings that result in 0 costs than settings with the other value
	        	relevantSettings = settingsZero;
	            // the default costs correspond to the formula's weight
	            defaultCosts = cost;
	        }
        }
        
        // if the smaller set contains no lines, this constraint is either unsatisfiable or a tautology, so it need not be considered at all
        if(relevantSettings.size() == 0)
        	return null;
        
        // construct the constraint
        Constraint c = new Constraint(defaultCosts, referencedVarIndices, relevantSettings.size());
        for(Tuple tuple : relevantSettings) {
        	c.addTuple(tuple);
        }
        
        if(this.cacheConstraints)
        	wcspConstraints.put(f, cost);

        return c;
    }
    
    protected void generateEvidenceConstraints(WCSP wcsp) throws ProbCogException {
    	long top = wcsp.top;
        // add unary constraints for evidence variables
        String[][] entries = db.getEntriesAsArray();
        WorldVariables worldVars = world.getVariables();
        for(String[] entry : entries) {
        	String varName = entry[0];
        	boolean isTrue = entry[1].equals(BooleanDomain.True);
        	
        	GroundAtom gndAtom = worldVars.get(varName);
        	Integer iVar = this.gndAtomIdx2varIdx.get(gndAtom.index);
        	if(iVar == null)
        		continue; // variable was removed due to simplification
        	
        	Vector<GroundAtom> block = this.varIdx2groundAtoms.get(iVar);
        	int iValue;        	
        	long tupleCost, defaultCost;
        	if(block.size()==1) {
        		iValue = isTrue ? 0 : 1;
        		defaultCost = top;
            	tupleCost = 0;
        	}
        	else {
        		iValue = block.indexOf(gndAtom);        		
        		if(isTrue) {
	        		defaultCost = top;
	            	tupleCost = 0;
        		}
        		else {
	        		defaultCost = 0;
	            	tupleCost = top;
        		}
        	}
        	int[] varIndices = new int[]{iVar};
        	Constraint c = new Constraint(defaultCost, varIndices, 1);
        	c.addTuple(new int[]{iValue}, tupleCost);
        	wcsp.addConstraint(c);
        	
        	if (this.cacheConstraints)
        		wcspConstraints.put(new GroundLiteral(!isTrue, gndAtom), top);
        }        
    }

    /**
     * recursive method to generate all the constraint lines for a formula
     * @param f formula (for this formula all possibilities are generated)
     * @param wcspVarIndices set of all ground atoms of the formula
     * @param i counter to terminate the recursion
     * @param w possible world to evaluate costs for a setting of ground atoms
     * @param domIndices current variable assignment
     * @param cost cost associated with the formula
     * @param settingsZero set to save all possibilities with costs of 0
     * @param settingsOther set to save all possibilities with costs different from 0
     * @throws ProbCogException 
     */
    protected void gatherConstraintTuples(Formula f, int[] wcspVarIndices, int i, PossibleWorld w, int[] domIndices, long cost, ArrayList<Tuple> settingsZero, ArrayList<Tuple> settingsOther) throws ProbCogException {
        // if all ground atoms were handled, the costs for this setting can be evaluated
        if (i == wcspVarIndices.length) {
            if(!f.isTrue(w))  // if formula is false, costs correspond to the weight
                settingsOther.add(new Tuple(domIndices.clone(), cost));  
            else // if formula is true, there are no costs
                settingsZero.add(new Tuple(domIndices.clone(), 0L)); 
        } else { // recursion  
        	int wcspVarIdx = wcspVarIndices[i];
            // get domain of the handled simplified variable
        	HashSet<String> domSet = doms.get(func_dom.get(vars.get(wcspVarIdx)));
        	int domSize;
        	if(domSet == null) // variable is boolean
        		domSize = 2;
        	else // variable is non-boolean (results from blocked ground atoms)
        		domSize = domSet.size();
    		for(int j = 0; j < domSize; j++) {
    			domIndices[i] = j;
    			setGroundAtomState(w, wcspVarIdx, j);
    			gatherConstraintTuples(f, wcspVarIndices, i + 1, w, domIndices, cost, settingsZero, settingsOther);
    		}
        }
    }
    
	public long getWorldCosts(IPossibleWorld world) throws ProbCogException {
		long costs = 0;
		for (Formula f : wcspConstraints.keySet()) {
			if (!f.isTrue(world)) {
				long newCosts = costs + wcspConstraints.get(f);
				if (newCosts < costs)
					throw new ProbCogException("Numeric overflow in costs");
				costs = newCosts;
			}
		}
		return costs;
	}
    
    protected void gatherConstraintTuplesSimplified(ComplexFormula f, int[] wcspVarIndices, long cost, ArrayList<Tuple> settings, boolean isConjunction) throws ProbCogException {
        // gather assignment
    	HashMap<Integer,Integer> assignment = new HashMap<Integer,Integer>();
        for(Formula child : f.children) {
        	boolean isTrue;
        	GroundAtom gndAtom;
        	if(child instanceof GroundLiteral) {
        		GroundLiteral lit = (GroundLiteral) child;        		
        		gndAtom = lit.gndAtom;
        		isTrue = lit.isPositive;
        	}       	
        	else if(child instanceof GroundAtom) {
        		gndAtom = (GroundAtom) child;
        		isTrue = true;
        	}
        	else
        		throw new SimplifiedConversionNotSupportedException("Child is not a literal");
        	if(!isConjunction) // for disjunction, consider the case where the child is false
        		isTrue = !isTrue; 
        	int wcspVarIdx = this.gndAtomIdx2varIdx.get(gndAtom.index);
        	Integer value = getVariableSettingFromGroundAtomSetting(wcspVarIdx, gndAtom, isTrue);
        	Integer oldValue = assignment.put(wcspVarIdx, value);
        	if(oldValue != null && oldValue != value) // formula contains the same variable twice with different value
        		throw new SimplifiedConversionNotSupportedException("Multiple appearances of the same variable");
        }
        
        int[] domIndices = new int[wcspVarIndices.length];
        int i = 0;
        for(Integer wcspVarIdx : wcspVarIndices) 
        	domIndices[i++] = assignment.get(wcspVarIdx);        

        // if the formula is true, we have no costs
        // if the formula is false, costs apply.
        // for conjunction, we considered the true case; for disjunction, we considered the false case
        settings.add(new Tuple(domIndices, isConjunction ? 0 : cost));
    }
    
    /**
     * sets the state of the ground atoms in w that correspond to the given wcsp variable
     * @param w
     * @param wcspVarIdx
     * @param domIdx index into the wcsp variable's domain
     */
    public void setGroundAtomState(PossibleWorld w, int wcspVarIdx, int domIdx) {
    	Vector<GroundAtom> atoms = varIdx2groundAtoms.get(wcspVarIdx);
    	if(atoms.size() == 1) { // var is boolean
    		w.set(atoms.iterator().next(), domIdx == 0);
    	}
    	else { // var corresponds to block
	    	Object[] dom = doms.get(func_dom.get(vars.get(wcspVarIdx))).toArray();
	    	setBlockState(w, atoms, dom[domIdx].toString());
    	}
    	//System.out.printf("%s = %s\n", this.simplifiedVars.get(wcspVarIdx), dom[domIdx].toString());
    }
    
    protected int getVariableSettingFromGroundAtomSetting(int wcspVarIdx, GroundAtom gndAtom, boolean isTrue) throws SimplifiedConversionNotSupportedException {
    	Vector<GroundAtom> atoms = varIdx2groundAtoms.get(wcspVarIdx);
    	if(atoms.size() == 1) {
    		return isTrue ? 0 : 1;
    	}
    	else {
    		if(!isTrue)
    			throw new SimplifiedConversionNotSupportedException("Blocked variable appears negated");
    		int idx = atoms.indexOf(gndAtom);
    		if(idx == -1)
    			throw new IllegalArgumentException("Ground atom does not appear in list");
    		return idx;
    	}
    }
    
    protected class SimplifiedConversionNotSupportedException extends ProbCogException {
    	public SimplifiedConversionNotSupportedException(String message) { super(message); }
    	
		private static final long serialVersionUID = 1L;
	}
    
    /**
     * this method sets the truth values of a block of mutually exclusive ground atoms 
     * @param block atoms within the block
     * @param value value indicating the atom to set to true
     */
    protected void setBlockState(PossibleWorld w, Vector<GroundAtom> block, String value) {
    	int detArgIdx = this.mln.getFunctionallyDeterminedArgument(block.iterator().next().predicate);
        Iterator<GroundAtom> it = block.iterator();
        GroundAtom g;
        while(it.hasNext()) {
            g = it.next();
            boolean v = g.args[detArgIdx].equals(value); 
            w.set(g.index, v);
            if(v)
            	break;
        }
        // set all remaining atoms false
        while(it.hasNext())
            w.set(it.next().index, false);
    }

    protected void initialize() throws ProbCogException {
    	this.db = mrf.getDb();
        this.world = new PossibleWorld(mrf.getWorldVariables());
        doms = mrf.getDb().getDomains();
        createVariables();
        simplifyVars(mrf.getDb());
        if (divisor == null) {
        	divisor = computeDivisor();
        	log.info("Computed divisor for weight to cost conversion: %g", divisor);
        }
        else {
        	log.info("Using divisor based on given scaling factor: %g", divisor);
        }
        
        // compute hard cost
    	long sumSoftCosts = 0;
        for(WeightedFormula wf : mrf) {
        	if(!wf.isHard) {
	        	long cost = Math.abs(Math.round(wf.weight / divisor));
                long newSum = sumSoftCosts + cost;
                if (newSum < sumSoftCosts)
                    throw new ProbCogException(String.format("Numeric overflow in sumSoftCosts (%d < %d)", newSum, sumSoftCosts));
	        	sumSoftCosts = newSum;
        	}
        }
        
        hardCost = sumSoftCosts + 1;
        if (hardCost <= sumSoftCosts)
            throw new ProbCogException("Numeric overflow in sumSoftCosts");
    }

	@Override
	public ParameterHandler getParameterHandler() {		
		return paramHandler;
	}

	@Override
	public boolean getVerboseMode() {
		return verbose;
	}

	@Override
	public boolean getDebugMode() {
		return debug;
	}
}
