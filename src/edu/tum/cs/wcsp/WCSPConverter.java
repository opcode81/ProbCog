/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.tum.cs.wcsp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.Vector;

import edu.tum.cs.logic.ComplexFormula;
import edu.tum.cs.logic.Conjunction;
import edu.tum.cs.logic.Disjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.IPossibleWorld;
import edu.tum.cs.logic.Negation;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.srl.BooleanDomain;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;

/**
 * Converts an instantiated MLN (i.e. a ground MRF) into the Toulbar2 WCSP format
 * @author wylezich, jain
 */
public class WCSPConverter {

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
	protected boolean initialized = false;
	int numConstraints = 0;
	protected long hardCost = -1;
	protected boolean debug = false;
	protected Database db;
	protected PrintStream out;
	protected boolean cacheConstraints = false;
	
    /**
     * @param mrf
     * @throws Exception 
     */
    public WCSPConverter(MarkovRandomField mrf) throws Exception {
        this.mln = mrf.mln;
        this.mrf = mrf;                        
    }
    
    public void setCacheConstraints(boolean cache) {
    	this.cacheConstraints = cache;
    }
    
    protected double getDivisor() {
        // get minimum weight and build sorted tree set of weights
    	TreeSet<Double> weight = new TreeSet<Double>();
        double minWeight = Double.MAX_VALUE;
        for(WeightedFormula wf : mln.getFormulas()) {
        	double w = Math.abs(wf.weight);
            weight.add(w);
            if(w < minWeight && w != 0)
            	minWeight = w;
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
        if(minWeight < 1.0)
        	divisor *= minWeight;
        if(deltaMin < 1.0)
        	divisor *= deltaMin;
        
        return divisor;
    }

    
    /**
     * performs the conversion of the ground MRF to the WCSP file
     * @param wcspFilename
     * @throws Exception 
     */
    public void run(String wcspFilename) throws Exception {
    	initialize();
        //out = new PrintStream(wcspFilename);
    	ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); 
    	out = new PrintStream(byteStream);        
        generateEvidenceConstraints();
        generateConstraints();        
        out.flush();
        out.close();
        
        PrintStream outFile = new PrintStream(wcspFilename);
        outFile.print(generateHead());
        outFile.print(byteStream.toString());
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
        
        if(debug) {
        	System.out.println("WCSP variables:");
        	for(Entry<Integer,Vector<GroundAtom>> e : varIdx2groundAtoms.entrySet()) {
        		System.out.printf("%s %s\n", e.getKey(), StringTool.join(", ", e.getValue()));
        	}
        }
    }

    /**
     * this method simplifies the generated variables (if a variable is given by the evidence, it's not necessary for the WCSP) 
     * @param variables all generated variables to check for evidence-entry
     * @param db evidence of the scenario
     * @throws Exception 
     */
    private void simplifyVars(Database db) throws Exception {
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
        
        System.out.printf("simplification: reduced %d to %d variables\n", vars.size(), simplifiedVars.size());
        this.vars = simplifiedVars;
        this.gndAtomIdx2varIdx = sf_gndAtomIdx2varIdx;
        this.varIdx2groundAtoms = sf_varIdx2groundAtoms; 
        
        /*
        if(debug) {
        	System.out.println("WCSP variables (simplified):");
        	for(Entry<Integer,HashSet<GroundAtom>> e : sfvars_gnd.entrySet()) {
        		System.out.printf("%d %s\n", e.getKey(), StringTool.join(", ", e.getValue()));
        	}
        }
        */        	
    }

    protected void generateConstraints() throws Exception {
        for(WeightedFormula wf : mrf)
        	generateConstraint(wf);
    }
    
    /**
     * this method generates a WCSP-Constraint for a formula
     * @param f formula (for this formula a WCSP- constraint is generated), the formula is already simplified
     * @param weight the weight of the formula to calculate the costs
     * @throws Exception 
     */
    protected void generateConstraint(WeightedFormula wf) throws Exception {
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

        // save index of the corresponding simplified variable in an array
        HashSet<Integer> setVarIndices = new HashSet<Integer>(gndAtoms.size());
        for(GroundAtom g : gndAtoms) {
            // add simplified variable only if the array doesn't contain this sf_variable already
        	Integer idx = gndAtomIdx2varIdx.get(g.index);
        	if(idx == null)
        		throw new Exception("Variable index for '" + g + "' is null");
        	setVarIndices.add(idx);
        }
        int[] referencedVarIndices = new int[setVarIndices.size()];
        int i = 0;
        for(Integer varIdx : setVarIndices)
        	referencedVarIndices[i++] = varIdx;

        // get cost value for this constraint
        long cost;
        if(wf.isHard)
        	cost = hardCost;
        else
        	cost = Math.round(weight / divisor);
        
        ArrayList<Pair<int[],Long>> relevantSettings = null;
        long defaultCosts = -1;
        
        // try the simplified conversion method 
        boolean generateAllPossibilities = true;
        boolean isConjunction = f instanceof Conjunction; 
        if(isConjunction || f instanceof Disjunction) {
        	generateAllPossibilities = false;
        	try {
        		relevantSettings = new ArrayList<Pair<int[],Long>>();
        		this.gatherConstraintLinesSimplified((ComplexFormula)f, referencedVarIndices, cost, relevantSettings, isConjunction);
        		defaultCosts = isConjunction ? cost : 0;
        	}
        	catch(SimplifiedConversionNotSupportedException e) {
        		generateAllPossibilities = true;
        	}
        }
        
        // if necessary, use the complex conversion method which looks at all possible settings
        if(generateAllPossibilities) {        
	        // generate all possibilities for this constraint
	        ArrayList<Pair<int[],Long>> settingsZero = new ArrayList<Pair<int[],Long>>();
	        ArrayList<Pair<int[],Long>> settingsOther = new ArrayList<Pair<int[],Long>>();
	        gatherConstraintLines(f, referencedVarIndices, 0, world, new int[referencedVarIndices.length], cost, settingsZero, settingsOther);                 
	        
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
        	return;
        
        // write results
        Constraint c = new Constraint(defaultCosts, referencedVarIndices, relevantSettings.size());
        for(Pair<int[],Long> tuple : relevantSettings) {
        	c.addTuple(tuple.first, tuple.second);
        }
        c.writeWCSP(out);
        
        if (this.cacheConstraints)
        	wcspConstraints.put(f, cost);
        
        numConstraints++;
    }

    /**
     * writes the header of the WCSP file
     * @param out the stream to write to
     * @throws Exception 
     */
    protected String generateHead() throws Exception {
    	if(!initialized)
    		throw new Exception("Not initialized");
    	
        int maxDomSize = 0;        

        // get list of domain sizes and maximum domain size
        StringBuffer strDomSizes = new StringBuffer();
        for(int i = 0; i < vars.size(); i++) {
        	HashSet<String> domSet = doms.get(func_dom.get(vars.get(i))); 
            int domSize = domSet == null ? 2 : domSet.size();
            strDomSizes.append(domSize + " ");
            if(domSize > maxDomSize)
                maxDomSize = domSize;
        }

        long top = hardCost;
        System.out.println("# variables: " + vars.size());
        System.out.println("top: " + top);
        System.out.println("divisor: " + divisor);       

        // the first line of the WCSP-File
        // syntax: name of the WCSP, number of variables, maximum domain size of the variables, number of constraints, initial TOP
        return String.format("WCSPfromMLN %d %d %d %d\n%s\n", vars.size(), maxDomSize, numConstraints, top, strDomSizes.toString());
    }
    
    protected void generateEvidenceConstraints() throws Exception {
    	long top = hardCost;
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
        	int iValue = -1;        	
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
        	c.writeWCSP(out);
        	
        	if (this.cacheConstraints)
        		wcspConstraints.put(new GroundLiteral(!isTrue, gndAtom), top);
        	
        	numConstraints++;
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
     * @throws Exception 
     */
    protected void gatherConstraintLines(Formula f, int[] wcspVarIndices, int i, PossibleWorld w, int[] domIndices, long cost, ArrayList<Pair<int[],Long>> settingsZero, ArrayList<Pair<int[],Long>> settingsOther) throws Exception {
        // if all ground atoms were handled, the costs for this setting can be evaluated
        if (i == wcspVarIndices.length) {
            if(!f.isTrue(w))  // if formula is false, costs correspond to the weight
                settingsOther.add(new Pair<int[],Long>(domIndices.clone(), cost));  
            else // if formula is true, there are no costs
                settingsZero.add(new Pair<int[],Long>(domIndices.clone(), 0L)); 
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
    			gatherConstraintLines(f, wcspVarIndices, i + 1, w, domIndices, cost, settingsZero, settingsOther);
    		}
        }
    }
    
	public long getWorldCosts(IPossibleWorld world) throws Exception {
		long costs = 0;
		for (Formula f : wcspConstraints.keySet()) {
			if (!f.isTrue(world)) {
				long newCosts = costs + wcspConstraints.get(f);
				if (newCosts < costs)
					throw new Exception("Numeric overflow in costs");
				costs = newCosts;
			}
		}
		return costs;
	}
    
    protected void gatherConstraintLinesSimplified(ComplexFormula f, int[] wcspVarIndices, long cost, ArrayList<Pair<int[], Long>> settings, boolean isConjunction) throws Exception {
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
        		throw new SimplifiedConversionNotSupportedException();
        	if(!isConjunction) // for disjunction, consider the case where the child is false
        		isTrue = !isTrue; 
        	int wcspVarIdx = this.gndAtomIdx2varIdx.get(gndAtom.index);
        	Integer value = getVariableSettingFromGroundAtomSetting(wcspVarIdx, gndAtom, isTrue);
        	Integer oldValue = assignment.put(wcspVarIdx, value);
        	if(oldValue != null && oldValue != value) // formula contains the same variable twice with different value
        		throw new SimplifiedConversionNotSupportedException();
        }
        
        int[] domIndices = new int[wcspVarIndices.length];
        int i = 0;
        for(Integer wcspVarIdx : wcspVarIndices) 
        	domIndices[i++] = assignment.get(wcspVarIdx);        

        // if the formula is true, we have no costs
        // if the formula is false, costs apply.
        // for conjunction, we considered the true case; for disjunction, we considered the false case
        settings.add(new Pair<int[],Long>(domIndices, isConjunction ? 0 : cost));
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
    			throw new SimplifiedConversionNotSupportedException();
    		int idx = atoms.indexOf(gndAtom);
    		if(idx == -1)
    			throw new IllegalArgumentException("Ground atom does not appear in list");
    		return idx;
    	}
    }
    
    protected class SimplifiedConversionNotSupportedException extends Exception {
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

    public void initialize() throws Exception {
    	this.db = mrf.getDb();
        this.world = new PossibleWorld(mrf.getWorldVariables());
        doms = mrf.getDb().getDomains();
        createVariables();
        simplifyVars(mrf.getDb());
        divisor = getDivisor();
        System.out.printf("divisor: %g\n", divisor);
        
    	long sumSoftCosts = 0;
        for(WeightedFormula wf : mrf) {
        	if(!wf.isHard) {
	        	long cost = Math.abs(Math.round(wf.weight / divisor));
                long newSum = sumSoftCosts + cost;
                if (newSum < sumSoftCosts)
                    throw new Exception(String.format("Numeric overflow in sumSoftCosts (%d < %d)", newSum, sumSoftCosts));
	        	sumSoftCosts = newSum;
        	}
        }
        
        hardCost = sumSoftCosts + 1;
        if (hardCost <= sumSoftCosts)
            throw new Exception("Numeric overflow in sumSoftCosts");
        
        initialized = true;
    }
}
