/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.tum.cs.wcsp;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.Negation;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.mln.GroundingCallback;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;

/**
 * Converts an instantiated MLN (i.e. a ground MRF) into the Toulbar2 WCSP format
 * @author wylezich, jain
 */
public class WCSPConverter implements GroundingCallback {

	protected MarkovLogicNetwork mln;
	protected PossibleWorld wld;
	protected Double deltaMin;
	protected HashMap<String, HashSet<String>> doms;
	protected HashMap<Integer, Integer> gndID_BlockID;
	protected ArrayList<String> vars;
	protected ArrayList<String> simplifiedVars;
	protected HashMap<GroundAtom, Integer> gnd_varidx;
	protected HashMap<Integer, Integer> gnd_sfvaridx;
	protected HashMap<String, HashSet<GroundAtom>> vars_gnd;
    /**
     * maps indices of simplified WCSP variables to sets of ground atoms
     */
	protected HashMap<Integer, HashSet<GroundAtom>> sfvars_gnd;
	protected HashMap<String, String> func_dom;
	protected HashMap<Integer, Integer> sfvars_vars;
	protected StringBuffer sb_result, sb_settings;
	protected PrintStream ps;
	protected int numConstraints = 0;
	protected boolean initialized = false;
	protected long sumSoftCosts = 0;

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
     * @param scenarioSettingsFilename (may be null)
     * @throws FileNotFoundException
     */
    public void run(String wcspFilename, String scenarioSettingsFilename) throws FileNotFoundException {
    	if(scenarioSettingsFilename != null) {
	        // save settings of szenario into a file
	        sb_settings = saveSzenarioSettings(new StringBuffer());
	        ps = new PrintStream(scenarioSettingsFilename);
	        ps.print(sb_settings);
	        ps.flush();
	        ps.close();
    	}

        // save generated WCSP-File in a file
    	//System.out.println("writing output to " + wcspFilename);
        ps = new PrintStream(wcspFilename);
        generateHead(ps);
        ps.print(sb_result);
        ps.flush();
        ps.close();
    }

    /**
     * this method saves all required mappings and variables in a file to restore the szenario
     * and display the solution 
     * @param sb StringBuffer to save the settings of the szenario
     * @return
     */
    private StringBuffer saveSzenarioSettings(StringBuffer sb) {

        // save domains
        sb.append("Domains:" + System.getProperty("line.separator"));
        for (Entry e : doms.entrySet())
            sb.append(e.getKey().toString() + "=" + e.getValue().toString() + ";");
        sb.append(System.getProperty("line.separator"));

        // save all variables
        sb.append("Variables:" + System.getProperty("line.separator"));
        for (String s : vars)
            sb.append(s + ";");
        sb.append(System.getProperty("line.separator"));

        // save mapping of groundatoms to variables 
        sb.append("gnd_varsidx:" + System.getProperty("line.separator"));
        for (Entry e : gnd_varidx.entrySet())
            sb.append(e.getKey().toString() + "=" + e.getValue().toString() + ";");
        sb.append(System.getProperty("line.separator"));

        // save domain for each variable
        sb.append("var_domain:" + System.getProperty("line.separator"));
        for (Entry e : func_dom.entrySet())
            sb.append(e.getKey().toString() + "=" + e.getValue().toString() + ";");
        sb.append(System.getProperty("line.separator"));

        // save mapping of variables to groundatoms
        sb.append("vars_gnd:" + System.getProperty("line.separator"));
        for (Entry e : vars_gnd.entrySet())
            sb.append(e.getKey().toString() + "=" + e.getValue().toString() + ";");
        sb.append(System.getProperty("line.separator"));

        // save mapping of simplified variables to variables
        sb.append("sfvars_vars:" + System.getProperty("line.separator"));
        for (Entry e : sfvars_vars.entrySet())
            sb.append(e.getKey().toString() + "=" + e.getValue().toString() + ";");
        sb.append(System.getProperty("line.separator"));

        return sb;
    }

    /**
     * this method generates a variable for each groundatom; for blocks only one variable is created
     */
    protected void atom2var() {
        vars = new ArrayList<String>();                         // arraylist, which contains new variables
        gnd_varidx = new HashMap<GroundAtom, Integer>();        // Hashmap that maps groundatom to the new variable
        func_dom = new HashMap<String, String>();               // Hashmap that maps variable to domain the variable uses
        vars_gnd = new HashMap<String, HashSet<GroundAtom>>();  // Hashmap that maps a variable to all groundatoms, that are set by this variable

        WorldVariables ww = wld.getVariables();
        for (int i = 0; i < ww.size(); i++) {
            // check whether ground atom is in a block
            if (ww.getBlock(ww.get(i).index) != null)
                atom2func(ww.get(i));
            else { // it's a boolean variable
                vars.add(ww.get(i).toString());
                gnd_varidx.put(ww.get(i), vars.indexOf(ww.get(i).toString()));
                // in this case, the mapping of this variable is set to "boolean" domain
                func_dom.put(vars.get(vars.indexOf(ww.get(i).toString())), "boolean");
                // in this case, the HashSet of Groundatoms only contains the selected Worldvariable
                HashSet<GroundAtom> tmp = new HashSet<GroundAtom>();
                tmp.add(ww.get(i));
                vars_gnd.put(vars.get(vars.indexOf(ww.get(i).toString())), tmp);
            }
        }
    }

    /**
     * this method checks whether a variable for a groundatom already exists;
     * if a block exists, groundatom is added to this blockvariable, 
     * otherwise a new blockvariable will be created
     * @param gnd groundatom to check for existing blockvariable
     */
    protected void atom2func(GroundAtom gnd) {
        String shortend = "";
        int x = 0;
        // generate the new variable by cutting the last entry of the values of the predicate
        for (int i = 0; i < gnd.args.length - 1; i++) {
            if (x++ > 0)
                shortend = shortend + ",";
            shortend = shortend + gnd.args[i];
        }
        String function = gnd.predicate + "(" + shortend + ")";     // the new variable is a shortend predicate without the last entry
        
        // if variable already exists, add a mapping for the groundatom and the existing variable
        // and add the groundatom to hashset of groundatoms which maps to this variable
        if (vars.contains(function)) { // blockvariable already exists (adding only the mappings to hashsets)
            gnd_varidx.put(gnd, vars.indexOf(function));        
            HashSet<GroundAtom> temp = vars_gnd.get(function);
            temp.add(gnd);
        } else { // if the variable doesn't exists, add a new variable and the required mappings
            vars.add(function);
            gnd_varidx.put(gnd, vars.indexOf(function));
            Signature sig = mln.getSignature(gnd.predicate);
            func_dom.put(function, sig.argTypes[mln.getFunctionallyDeterminedArgument(gnd.predicate)]);
            HashSet<GroundAtom> temp = new HashSet<GroundAtom>();
            temp.add(gnd);
            vars_gnd.put(vars.get(vars.indexOf(function)), temp);
        }
    }

    /**
     * this method simplifies the generated variables (if a variable is given by the evidence, it's not necessary for the WCSP) 
     * @param variables all generated variables to check for evidence-entry
     * @param db evidence of the szenario
     * @throws Exception 
     */
    private void simplyfyVars(ArrayList<String> variables, Database db) throws Exception {      
        sfvars_vars = new HashMap<Integer, Integer>();  // mapping of simplified variables to variables
        simplifiedVars = new ArrayList<String>();       // arraylist of simplified variables
        gnd_sfvaridx = new HashMap<Integer, Integer>(); // mapping of groundatom to simplified variable
        sfvars_gnd = new HashMap<Integer, HashSet<GroundAtom>>();   // mapping of simplified variable to groundatom
        
        // check all variables for an evidence-entry
        for (int i = 0; i < variables.size(); i++) {
            HashSet<GroundAtom> givenAtoms = new HashSet<GroundAtom>();
            // check all entries in HashSet of the selected variable for an entry in evidence
            HashSet<GroundAtom> gndAtoms = vars_gnd.get(variables.get(i)); 
            for (GroundAtom g : gndAtoms) {
		           if (db.getVariableValue(g.toString(), false) != null) // evidence- entry exists
		                givenAtoms.add(g);
            }
            
            // if hashsets (givenAtoms and hashset of the variable) have same size, then all Goundatoms are set by the evidence
            // we don't need to handle this variable anymore
            // if hashsets don't have same size, the variable must be handled
            if ((gndAtoms.size() != givenAtoms.size())) {
                // add variable to simplifiedVars
            	int idx = simplifiedVars.size();
                simplifiedVars.add(variables.get(i));
                // save mapping of simplified variable and variable
                sfvars_vars.put(idx, i);
                // save mapping of groundatoms to the new simplified variable
                for (GroundAtom g : vars_gnd.get(variables.get(i)))
                    gnd_sfvaridx.put(g.index, idx);
                // clone hashset and save it in the mapping of simplified variables to groundatoms 
                sfvars_gnd.put(idx, (HashSet<GroundAtom>) gndAtoms.clone());
            }
        }
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
        	int idx = gnd_sfvaridx.get(g.index);
            if (!referencedVarIndices.contains(idx)) 
                referencedVarIndices.add(idx);
        }
        
        // generate all possibilities for this constraint
        ArrayList<String> settingsZero = new ArrayList<String>();
        ArrayList<String> settingsOther = new ArrayList<String>();        
        convertFormula(f, referencedVarIndices, 0, wld, new int[referencedVarIndices.size()], weight, settingsZero, settingsOther);
        ArrayList<String> smallerSet;
        long cost = Math.round(wf.weight / deltaMin); 
        long defaultCosts;
        if(settingsOther.size() < settingsZero.size()) { // in this case there are more null-values than lines with a value differing form 0
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
        if(!wf.isHard)
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
     */
    protected void generateHead(PrintStream out) {
        int maxDomSize = 0;        

        // get list of domain sizes and maximum domain size
        StringBuffer strDomSizes = new StringBuffer();
        for(int i = 0; i < simplifiedVars.size(); i++) {
        	HashSet<String> domSet = doms.get(func_dom.get(simplifiedVars.get(i))); 
            int domSize = domSet == null ? 2 : domSet.size();
            strDomSizes.append(domSize + " ");
            if(domSize > maxDomSize)
                maxDomSize = domSize;
        }

        // the first line of the WCSP-File
        // syntax: name of the WCSP, number of variables, maximum domainsize of the variables, number of constraints, initial TOP
        long top = sumSoftCosts+1;
        out.printf("WCSPfromMLN %d %d %d %d\n", simplifiedVars.size(), maxDomSize, numConstraints, top);
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
        	HashSet<String> domSet = doms.get(func_dom.get(simplifiedVars.get(wcspVarIdx)));
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
    	HashSet<GroundAtom> atoms = sfvars_gnd.get(wcspVarIdx);
    	if(atoms.size() == 1) { // var is boolean
    		w.set(atoms.iterator().next(), domIdx == 0);
    	}
    	else { // var corresponds to block
	    	Object[] dom = doms.get(func_dom.get(simplifiedVars.get(wcspVarIdx))).toArray();
	    	setBlockState(w, atoms, dom[domIdx].toString());
    	}
    	//System.out.printf("%s = %s\n", this.simplifiedVars.get(wcspVarIdx), dom[domIdx].toString());
    }
    
    /**
     * this method sets the truth values of a block of mutually exclusive ground atoms 
     * @param block atoms within the block
     * @param value value indicating the atom to set to true
     * TODO this method makes evil assumptions about the blocking of variables 
     */
    protected void setBlockState(PossibleWorld w, HashSet<GroundAtom> block, String value) {
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
            this.wld = new PossibleWorld(mrf.getWorldVariables());
            doms = mrf.getDb().getDomains();
            atom2var();
            simplyfyVars(vars, mrf.getDb());
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
