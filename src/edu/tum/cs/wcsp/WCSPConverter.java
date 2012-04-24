/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.tum.cs.wcsp;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Vector;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.Negation;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.srl.BooleanDomain;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.mln.GroundingCallback;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.util.StringTool;

/**
 * Converts an instantiated MLN (i.e. a ground MRF) into the Toulbar2 WCSP format
 * @author wylezich, jain
 */
public class WCSPConverter implements GroundingCallback {

	protected MarkovLogicNetwork mln;
	protected PossibleWorld world;
	protected Double deltaMin;
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
	protected StringBuffer sb_result, sb_settings;
	protected PrintStream ps;
	protected int numConstraints = 0;
	protected boolean initialized = false;
	protected long sumSoftCosts = 0;
	protected boolean debug = false;
	protected Database db;

    /**
     * Note: This constructor is more memory-efficient as does not require the whole set of ground formulas to be materialized in an MRF
     * @param mlnFileLoc MLN file
     * @param dbFileLoc  MLN database file
     * @throws Exception 
     */
    public WCSPConverter(String mlnFileLoc, String dbFileLoc) throws Exception {
        this.mln = new MarkovLogicNetwork(mlnFileLoc);
    	deltaMin = mln.getdeltaMin();
        sb_result = new StringBuffer();
        Database db = new Database(mln);
        db.readMLNDB(dbFileLoc);
        mln.ground(db, false, this); // implicitly performs the conversion of formulas to WCSP constraints through the callback
    }
    
    /**
     * @param mrf
     * @throws Exception 
     */
    public WCSPConverter(MarkovRandomField mrf) throws Exception {
        this.mln = mrf.mln;
    	deltaMin = mln.getdeltaMin();
        sb_result = new StringBuffer();        
        for(WeightedFormula wf : mrf) {
        	convertFormula(wf, mrf);
        }
    }
    
    /**
     * performs the conversion of the ground MRF to the WCSP file
     * @param wcspFilename
     * @throws Exception 
     */
    public void run(String wcspFilename) throws Exception {
        // save generated WCSP-File in a file
    	//System.out.println("writing output to " + wcspFilename);
        ps = new PrintStream(wcspFilename);
        generateHead(ps);
        ps.print(sb_result);
        ps.flush();
        ps.close();
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

    /**
     * this method generates a WCSP-Constraint for a formula
     * @param f formula (for this formula a WCSP- constraint is generated), the formula is already simplified
     * @param weight the weight of the formula to calculate the costs
     * @throws Exception 
     */
    protected void convertFormula(WeightedFormula wf) throws Exception {
    	Formula f = wf.formula;
    	double weight = wf.weight;
    	
        // get all groundatoms of the formula
        HashSet<GroundAtom> gndAtoms = new HashSet<GroundAtom>();
        f.getGroundAtoms(gndAtoms);

        // save index of the corresponding simplified variable in an array
        ArrayList<Integer> referencedVarIndices = new ArrayList<Integer>(gndAtoms.size());
        for (GroundAtom g : gndAtoms) {
            // add simplified variable only if the array doesn't contain this sf_variable already
        	Integer idx = gndAtomIdx2varIdx.get(g.index);
        	if(idx == null)
        		throw new Exception("Variable index for '" + g + "' is null");
            if (!referencedVarIndices.contains(idx)) 
                referencedVarIndices.add(idx);
        }
        
        // generate all possibilities for this constraint
        ArrayList<String> settingsZero = new ArrayList<String>();
        ArrayList<String> settingsOther = new ArrayList<String>();        
        convertFormula(f, referencedVarIndices, 0, world, new int[referencedVarIndices.size()], weight, settingsZero, settingsOther);
        ArrayList<String> smallerSet;
        long cost = Math.round(wf.weight / deltaMin); 
        long defaultCosts;
        if(settingsOther.size() < settingsZero.size()) { // in this case there are more null-values than lines with a value differing from 0
        	smallerSet = settingsOther;
            // the default costs (0) are calculated and set in the first line of the constraint
            defaultCosts = 0;
        } 
        else { // there are fewer settings that result in 0 costs than settings with the other value
        	smallerSet = settingsZero;
            // the default costs correspond to the formula's weight
            defaultCosts = cost;
        }
        
        // if the smaller set contains no lines, this constraint is either unsatisfiable or a tautology, so it need not be considered at all
        if(smallerSet.size() == 0)
        	return;

        numConstraints++;
        // TODO ideally, we should set the costs of hard constraints to the sumSoftCosts+1
        if(!wf.isHard || true)
        	sumSoftCosts += cost;
        
        // write results
        String nl = System.getProperty("line.separator");
        // first line of the constraint: arity of the constraint followed by indices of the variables, default costs, and number of constraint lines
        sb_result.append(referencedVarIndices.size() + " ");
        for(Integer in : referencedVarIndices) {
            sb_result.append(in + " ");
        }        
        sb_result.append(defaultCosts + " " + smallerSet.size());        
        sb_result.append(nl);
        // all lines differing from default costs are appended to the string buffer
        for (String s : smallerSet)
            sb_result.append(s + nl);
    }

    /**
     * writes the header of the WCSP file
     * @param out the stream to write to
     * @throws Exception 
     */
    protected void generateHead(PrintStream out) throws Exception {
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

        // the first line of the WCSP-File
        // syntax: name of the WCSP, number of variables, maximum domainsize of the variables, number of constraints, initial TOP
        long top = sumSoftCosts+1;
        
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
        	if(block.size()==1) {
        		iValue = isTrue ? 0 : 1;
        		this.sb_result.append(String.format("1 %d %d 1\n", iVar, top));
            	this.sb_result.append(String.format("%d 0\n", iValue));
        	}
        	else {
        		iValue = block.indexOf(gndAtom);        		
        		if(isTrue) {
	        		this.sb_result.append(String.format("1 %d %d 1\n", iVar, top));
	            	this.sb_result.append(String.format("%d 0\n", iValue));
        		}
        		else {
	        		this.sb_result.append(String.format("1 %d 0 1\n", iVar));
	            	this.sb_result.append(String.format("%d %d\n", iValue, top));
        		}
        	}    	
        	numConstraints++;
        }       
        
        System.out.println("# variables: " + vars.size());
        System.out.println("top: " + top);
        System.out.println("deltaMin: " + deltaMin);
        out.printf("WCSPfromMLN %d %d %d %d\n", vars.size(), maxDomSize, numConstraints, top);
        
        // the second line contains the domain size of each simplified variable of the WCSP
        out.println(strDomSizes.toString());
    }

    /**
     * recursive method to generate all possibilities for a formula
     * @param f formula (for this formula all possiblilities are generated)
     * @param wcspVarIndices set of all groundatoms of the formula
     * @param i counter to terminate the recursion
     * @param w possible world to evaluate costs for a setting of groundatoms
     * @param g array to save the current allocation of the variable
     * @param weight weight of the formula to calculate costs
     * @param settingsZero set to save all possibilities with costs of 0
     * @param settingsOther set to save all possibilities with costs different to 0
     * @throws Exception 
     */
    protected void convertFormula(Formula f, ArrayList<Integer> wcspVarIndices, int i, PossibleWorld w, int[] g, double weight, ArrayList<String> settingsZero, ArrayList<String> settingsOther) throws Exception {
    	if(weight < 0)
    		throw new Exception("Weights must be positive");
        // if all groundatoms were handled, the costs for this setting can be evaluated
        if (i == wcspVarIndices.size()) {
            StringBuffer zeile = new StringBuffer();
            // print the allocation of variables
            for (Integer c : g)
                zeile.append(c + " ");
            
            // calculate weight according to WCSP-Syntax
            if (!f.isTrue(w)) { // if formula is false, costs correspond to the weight
                zeile.append(Math.round(weight / deltaMin));
                settingsOther.add(zeile.toString());  
            } else { // if formula is true, there are no costs
                zeile.append("0");
                settingsZero.add(zeile.toString()); 
            }
        } else { // recursion  
        	int wcspVarIdx = wcspVarIndices.get(i);
            // get domain of the handled simplified variable
        	HashSet<String> domSet = doms.get(func_dom.get(vars.get(wcspVarIdx)));
        	int domSize;
        	if(domSet == null) // variable is boolean
        		domSize = 2;
        	else // variable is non-boolean (results from blocked ground atoms)
        		domSize = domSet.size();
    		for(int j = 0; j < domSize; j++) {
    			g[i] = j;
    			setGroundAtomState(w, wcspVarIdx, j);
    			convertFormula(f, wcspVarIndices, i + 1, w, g, weight, settingsZero, settingsOther);
    		}
        }
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

    /**
     * this method is the callback-method of the ground_and_simplify method;
     * it generates a WCSP-Constraint for the given formula
     * @param f a ground formula
     * @param weight the weight of the formula
     * @param db evidence of the scenario
     * @throws Exception 
     */
    public void onGroundedFormula(WeightedFormula wf, MarkovRandomField mrf) throws Exception {
    	convertFormula(wf, mrf);
    }
    
    public void convertFormula(WeightedFormula wf, MarkovRandomField mrf) throws Exception {
    	// initialization (necessary to perform here if working through callback)
        if(!initialized) { 
        	this.db = mrf.getDb();
            this.world = new PossibleWorld(mrf.getWorldVariables());
            doms = mrf.getDb().getDomains();
            createVariables();
            simplifyVars(mrf.getDb());
            initialized = true;
        }
        
        // if the weight is negative, negate the formula and its weight
        if(wf.weight < 0) {
        	wf.formula = new Negation(wf.formula);
        	wf.weight *= -1;
        }
        
        // perform actual conversions
        convertFormula(wf);
    }   
}
