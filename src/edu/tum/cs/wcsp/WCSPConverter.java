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
import java.util.Vector;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Database.Variable;
import edu.tum.cs.srl.mln.GroundingCallback;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;

/**
 * Converts an instantiated MLN (i.e. a ground MRF) into the Toulbar2 WCSP format
 * @author wylezich
 */
public class WCSPConverter implements GroundingCallback {

    MarkovRandomField mrf;
    MarkovLogicNetwork mln;
    PossibleWorld wld;
    Double deltaMin;
    HashMap<String, HashSet<String>> doms;
    HashMap<Integer, Integer> gndID_BlockID;
    ArrayList<String> vars;
    ArrayList<String> simplifiedVars;
    HashMap<GroundAtom, Integer> gnd_varidx;
    HashMap<Integer, Integer> gnd_sfvaridx;
    HashMap<String, HashSet<GroundAtom>> vars_gnd;
    HashMap<Integer, HashSet<GroundAtom>> sfvars_gnd;
    HashMap<String, String> func_dom;
    HashMap<Integer, Integer> sfvars_vars;
    StringBuffer sb_result, sb_settings;
    PrintStream ps;
    ArrayList<String> setwithnull;
    ArrayList<String> setwithvalue;
    long start = System.currentTimeMillis();
    int generatedFormulas = 0;

    /**
     * Constructor to instantiate a WCSP-Converter
     * @param mlnFileLoc filelocation of the MLN-File
     * @param dbFileLoc  filelocation of the evidence
     * @throws Exception 
     */
    public WCSPConverter(String mlnFileLoc, String dbFileLoc) throws Exception {
        this.mln = new MarkovLogicNetwork(mlnFileLoc, false, this);
    	deltaMin = mln.getdeltaMin();
        sb_result = new StringBuffer();
        this.mrf = mln.ground(dbFileLoc); // implicitly performs the conversion of formulas to WCSP constraints through the callback
    }
    
    public WCSPConverter(MarkovRandomField mrf) {
        this.mln = mrf.mln;
    	deltaMin = mln.getdeltaMin();
        sb_result = new StringBuffer();
        this.mrf = mrf;
        for(WeightedFormula wf : mrf) {
        	convertFormula(wf.formula, wf.weight, mrf.getDb());
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
        ps.print(generateHead(new StringBuffer()));
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
    private void atom2var() {
        vars = new ArrayList<String>();                         // arraylist, which contains new variables
        gnd_varidx = new HashMap<GroundAtom, Integer>();        // Hashmap that maps groundatom to the new variable
        func_dom = new HashMap<String, String>();               // Hashmap that maps variable to domain the variable uses
        vars_gnd = new HashMap<String, HashSet<GroundAtom>>();  // Hashmap that maps a variable to all groundatoms, that are set by this variable

        WorldVariables ww = mrf.getWorldVariables();
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
    private void atom2func(GroundAtom gnd) {
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
            func_dom.put(function, mln.getBlock().get(mrf.getWorldVariables().get(gnd.index).predicate.toString()));
            HashSet<GroundAtom> temp = new HashSet<GroundAtom>();
            temp.add(gnd);
            vars_gnd.put(vars.get(vars.indexOf(function)), temp);
        }
    }

    /**
     * this method simplifies the generated variables (if a variable is given by the evidence, it's not necessary for the WCSP) 
     * @param variables all generated variables to check for evidence-entry
     * @param db evidence of the szenario
     */
    private void simplyfyVars(ArrayList<String> variables, Database db) {      
        sfvars_vars = new HashMap<Integer, Integer>();  // mapping of simplified variables to variables
        simplifiedVars = new ArrayList<String>();       // arraylist of simplified variables
        gnd_sfvaridx = new HashMap<Integer, Integer>(); // mapping of groundatom to simplified variable
        sfvars_gnd = new HashMap<Integer, HashSet<GroundAtom>>();   // mapping of simplified variable to groundatom
        
        // check all variables for an evidence-entry
        for (int i = 0; i < variables.size(); i++) {
            HashSet<GroundAtom> givenAtoms = new HashSet<GroundAtom>();
            // check all entries in HashSet of the selected variable for an entry in evidence
            for (GroundAtom g : vars_gnd.get(variables.get(i))) {
                try {
                    if (db.getVariableValue(g.toString(), false) != null) // evidence- entry exists
                        givenAtoms.add(g);
                } catch (Exception ex) {
                    Logger.getLogger(WCSPConverter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            // if hashsets (givenAtoms and hashset of the variable) have same size, then all Goundatoms are set by the evidence
            // we don't need to handle this variable anymore
            // if hashsets don't have same size, the variable must be handeld
            if ((vars_gnd.get(variables.get(i)).size() != givenAtoms.size())) {
                // add variable to simplifiedVars
                simplifiedVars.add(variables.get(i));
                // save mapping of simplified variable and variable
                sfvars_vars.put(simplifiedVars.indexOf(variables.get(i)), i);
                // save mapping of groundatoms to the new simplified variable
                for (GroundAtom g : vars_gnd.get(variables.get(i)))
                    gnd_sfvaridx.put(g.index, simplifiedVars.indexOf(variables.get(i)));
                // clone hashset and save it in the mapping of simplified variables to groundatoms 
                HashSet<GroundAtom> tmp = (HashSet<GroundAtom>) vars_gnd.get(variables.get(i)).clone();
                sfvars_gnd.put((simplifiedVars.indexOf(variables.get(i))), tmp);
            }
        }
    }

    /**
     * this method generates a WCSP-Constraint for a formula
     * @param f formula (for this formula a WCSP- constraint is generated), the formula is already simplified
     * @param weight the weight of the formula to calculate the costs
     */
    protected void convertFormula(Formula f, double weight) {
        // get all groundatoms of the formula
        HashSet<GroundAtom> gndAtoms = new HashSet<GroundAtom>();
        f.getGroundAtoms(gndAtoms);

        // save index of the corresponding simplified variable in an array
        ArrayList<Integer> ar = new ArrayList<Integer>(gndAtoms.size());
        for (GroundAtom g : gndAtoms) {
            // add simplified variable only if the array doesn't contains this sf_variable already
            if (!ar.contains(gnd_sfvaridx.get(g.index))) 
                ar.add(gnd_sfvaridx.get(g.index));
        }
        
        // write the first line of the contraint in the stringbuffer
        // syntax: arity of the contraint, indices of the variables
        sb_result.append(ar.size() + " ");
        int value = 1;
        for (Integer in : ar) {
            sb_result.append(in + " ");
            // the maximum number of entries is calculated to set the capacity of the sets
            // this value is also needed for the large-version of the WCSP
            value = value * doms.get(func_dom.get(vars.get(sfvars_vars.get(in)))).size();
        }

        setwithnull = new ArrayList<String>(value);
        setwithvalue = new ArrayList<String>(value);
        
        // recursive method to gerenate all possibilities for this contraint
        Vector<ArrayList<String>> vec = convertFormula(f, ar, 0, wld, new int[ar.size()], weight, setwithnull, setwithvalue);
        if (vec.size() > 0) {
            Double zahl;
            //vec(0) = setwithvalue vec(1) = setwithnull
            if (vec.get(0).size() < vec.get(1).size()) { // in this case there are more null-values than lines with a value differing form 0
                // the defaultcosts (0) are calculated and set in the first line of the constraint
                zahl = (0.0 * (-1.0) + Math.abs(Math.min(weight * (-1.0), 0.0)));
                sb_result.append(Math.round(zahl / deltaMin) + " " + vec.get(0).size());
                // first line is completed
                sb_result.append(System.getProperty("line.separator"));
                // all lines differing from defaultcosts are appended to the stringbuffer
                for (String s : vec.get(0))
                    sb_result.append(s + System.getProperty("line.separator"));

            } else { // in this case there are less "null-values" than lines with a value differing from 0
                // the defaultcosts (value <> 0) are calculated and set in the first line of the constraint
                zahl = (weight * (-1.0) + Math.abs(Math.min(weight * (-1.0), 0.0)));
                sb_result.append(Math.round(zahl / deltaMin) + " " + vec.get(1).size());
                // first line is completed
                sb_result.append(System.getProperty("line.separator"));
                // all lines differing from defaultcosts are appended to the stringbuffer
                for (String s : vec.get(1))
                    sb_result.append(s + System.getProperty("line.separator"));

            }
        }
    }

    /**
     * this method generates the head of the WCSP-File
     * @param s stringbuffer to save the head of the WCSP-File
     * @return
     */
    private StringBuffer generateHead(StringBuffer s) {
        int domsize = 0, tempdomsize = 0;
        String str = "";

        // calculate the arity for each domain of the simplified variables
        for (int i = 0; i < simplifiedVars.size(); i++) {
            tempdomsize = doms.get(func_dom.get(simplifiedVars.get(i))).size();
            str = str + tempdomsize + " ";
            if (tempdomsize > domsize)
                domsize = tempdomsize;
        }

        // the first line of the WCSP-File
        // syntax: name of the WCSP, number of variables, maximum domainsize of the variables, number of constraints, initial TOP
        String tmpstring = "Raumplan " + simplifiedVars.size() + " " + domsize + " " + generatedFormulas + "" + " 80000";
        s.append(tmpstring);
        s.append(System.getProperty("line.separator"));
        // the second line contains the arity of each simplified variable of the WCSP
        s.append(str);
        s.append(System.getProperty("line.separator"));
        return s;
    }

    /**
     * recursive method to generate all possibilities for a formula
     * @param f formula (for this formula all possiblilities are generated)
     * @param wcspVarIndices set of all groundatoms of the formula
     * @param i counter to terminate the recursion
     * @param w possibleworld to evaluate costs for a setting of groundatoms
     * @param g array to save the current allocation of the variable
     * @param weight weight of the formula to calculate costs
     * @param swn set to save all possibilities with costs of 0
     * @param swv set to save all possibilities with costs different to 0
     * @return returns a Vector that contains the two sets where all constraints of this WCSP-constraint are saved
     */
    private Vector<ArrayList<String>> convertFormula(Formula f, ArrayList<Integer> wcspVarIndices, int i, PossibleWorld w, int[] g, double weight, ArrayList<String> swn, ArrayList<String> swv) {
        // if all groundatoms were handled, the costs for this setting can be evaluated
        if (i == wcspVarIndices.size()) {
            StringBuffer zeile = new StringBuffer();
            Double zahl;
            // print the allocation of variables
            for (Integer c : g)
                zeile.append(c + " ");
            
            // calculate weight according to WCSP-Syntax
            if (!f.isTrue(w)) { // if formula is false, costs are set to the weight
                zahl = (0.0 + Math.abs(Math.min(weight * (-1.0), 0.0)));
                zeile.append(Math.round(zahl / deltaMin));
                swn.add(zeile.toString());  // add this line to the according set
            } else { // if formula is true, costs are set to 0
                zahl = (weight * (-1.0) + Math.abs(Math.min(weight * (-1.0), 0.0)));
                zeile.append(Math.round(zahl / deltaMin));
                swv.add(zeile.toString()); // add this line to the according set
            }
        } else { // recursion  
            // get domain of the handled simplified variable 
            Object[] dom = doms.get(func_dom.get(simplifiedVars.get(wcspVarIndices.get(i)))).toArray();
            // get the groundatoms this simplified variable is mapped to
            HashSet<GroundAtom> atoms = sfvars_gnd.get((wcspVarIndices.get(i)));
            for (int z = 0; z < dom.length; z++) {
                g[i] = z;   // save the setting of the variable in the array
                 // set possibleWorld (the atom which maps to the selected value of the domain is set true, all other atoms of this variable are set false)
                setWorldofWCSP(w, atoms, dom[z].toString());    
                // call method again (with the new settings)
                convertFormula(f, wcspVarIndices, i + 1, w, g, weight, swn, swv);
            }
        }
        // when all possibilities are generated, save the two sets in a vector and return it
        Vector<ArrayList<String>> vec = new Vector<ArrayList<String>>();
        vec.add(swv);
        vec.add(swn);
        return vec;
    }
    
    /**
     * sets the state of the ground atoms in w that correspond to the given wcsp variable
     * @param w
     * @param wcspVarIdx
     * @param domIdx index into the wcsp variable's domain
     */
    public void setGroundAtomState(PossibleWorld w, int wcspVarIdx, int domIdx) {
    	HashSet<GroundAtom> atoms = sfvars_gnd.get(wcspVarIdx);
    	Object[] dom = doms.get(func_dom.get(simplifiedVars.get(wcspVarIdx))).toArray();
    	setWorldofWCSP(w, atoms, dom[domIdx].toString());
    }
    
    /**
     * this method sets the truth values of a block of mutually exclusive ground atoms 
     * @param atoms atoms within the block
     * @param value value indicating the atom to set to true
     * TODO this method makes bad assumptions about the blocking of variables 
     */
    protected void setWorldofWCSP(PossibleWorld w, HashSet<GroundAtom> atoms, String value) {
        Iterator<GroundAtom> it = atoms.iterator();
        GroundAtom g;
        // sets all atoms of the hashset true or false
        while (it.hasNext()) {
            g = it.next();
            // if atom does't contains value and value isn't boolean -> set atom false
            if (!(g.args[g.args.length - 1].hashCode() == value.hashCode()) && !value.equals("True"))
                w.set(g.index, false);
            else {
                // else set atom true and break
                w.set(g.index, true);
                break;
            }
        }

        // set all left atoms false
        while (it.hasNext())
            w.set(it.next().index, false);
    }

    
    /**
     * this method sets the possibleWorld (initial) to the values given by the evidence
     */
    private void setBlocks() {
        HashMap<String, Boolean> evidence = new HashMap<String, Boolean>();
        gndID_BlockID = new HashMap<Integer, Integer>();
        int countAtoms = mrf.getWorldVariables().size();
        for (Variable var : mrf.getDb().getEntries()) {
            String strGndAtom = var.getPredicate(mln);
            GroundAtom gndAtom = mrf.getWorldVariables().get(strGndAtom);
            Block block = mrf.getWorldVariables().getBlock(gndAtom.index);
            boolean set = false;
            if (block != null && var.isTrue()) {
                countAtoms++;
                for (GroundAtom ga : block) {
                    if (!gndID_BlockID.containsKey(ga.index)) {
                        gndID_BlockID.put(ga.index, countAtoms);
                        set = true;
                    }
                    if (ga.toString().equals(strGndAtom)) {
                        if (!evidence.containsKey(ga.toString()))
                            evidence.put(ga.toString(), var.isTrue());
                    } else {
                        if (!evidence.containsKey(ga.toString()))
                            evidence.put(ga.toString(), !var.isTrue());
                    }
                }
                if (!set)
                    countAtoms--;
            } else
                evidence.put(strGndAtom, var.isTrue());
        }
        for (Entry<String, Boolean> e : evidence.entrySet())
            wld.set(e.getKey(), e.getValue());
    }

    
    /**
     * this method is the callback-method of the ground_and_simplify method;
     * it generates a WCSP-Constraint for the given formula
     * @param f a ground formula
     * @param weight the weight of the formula
     * @param db evidence of the scenario
     */
    public void onGroundedFormula(Formula f, double weight, Database db) {
    	convertFormula(f, weight, db);
    }
    
    public void convertFormula(Formula f, double weight, Database db) {
        if (generatedFormulas++ < 1) { // set initial mappings of the WCSP-Converter
            this.wld = new PossibleWorld(mrf.getWorldVariables());
            doms = db.getDomains();
            atom2var();
            simplyfyVars(vars, db);
        }
        // call the recursive method to generate all possibilities for formula f
        convertFormula(f, weight);

        //debug
        if (generatedFormulas % 5000 == 0) {
            //System.out.println("Zeit für 5000 Formeln: " + ((System.currentTimeMillis() - start) / 1000.0) + " secs");
            start = System.currentTimeMillis();
        }
    }   
}
