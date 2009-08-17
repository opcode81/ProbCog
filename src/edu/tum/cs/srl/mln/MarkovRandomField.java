/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.tum.cs.srl.mln;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.ParameterGrounder;
import edu.tum.cs.srl.Signature;

/**
 * Class that represents a grounded instance of a MLN-file
 * @author wernickr
 */
public class MarkovRandomField implements Iterable<WeightedFormula> {
    protected Database db;
    public MarkovLogicNetwork mln;
    protected String dbFile;
    protected Vector<WeightedFormula> weightedFormulas;
    protected WorldVariables vars;
    
    /**
     * Constructor of a MarkovRandomField
     * @param mln an instance of a MLN
     * @param dbFileLoc filelocation of evidence 
     * @param makelist boolean to set, whether a list of grounded formulas should be generated
     * @param gc an optional callback method (called for each grounded formula)
     * @throws Exception 
     */
    public MarkovRandomField(MarkovLogicNetwork mln, String dbFileLoc, boolean makelist, GroundingCallback gc) throws Exception{
        db = new Database(mln);
        db.readMLNDB(new File(dbFileLoc).toString());
        this.vars = new WorldVariables();
        this.mln = mln;
        this.dbFile = dbFileLoc;
        db.readMLNDB(dbFile);
        groundVariables();
        createBlocks();
        groundFormulas(makelist, gc);
    } 
    
    /**
     * Method that returns worldVariables of the given MLN
     * @return
     */
    public WorldVariables getWorldVariables() {
        return vars;
    }
    
    /**
     * creates ground atoms for all signatures
     * @throws Exception 
     */
    protected void groundVariables() throws Exception {
        Collection<Signature> signa = mln.signatures.values();
        for (Signature pre : signa) {
            Collection<String[]> col = ParameterGrounder.generateGroundings(mln.getSignature(pre.functionName), db);
            for(String[] grAtom : col) {
                GroundAtom gnd = new GroundAtom(pre.functionName, grAtom);
                vars.add(gnd);
            }
        }
    }
    
    /**
     * creates blocks for ground atoms (only for atoms that are block variables)
     */
    protected void createBlocks(){
        for (String key : mln.block.keySet()) {
            String[] domains = mln.getSignature(key).argTypes;
            int index = mln.getPosinArray(mln.block.get(key), domains);
            Vector<GroundAtom> atoms = new Vector<GroundAtom>();
            atoms = getGAforBlock(key + "*");
            int c = 0;
            while (atoms != null && atoms.size() > 0) {
                String partofAtom = "";
                String atomString = atoms.get(c).toString();
                for (int d = 0; d <= domains.length - 1; d++) {
                    if (d == index) {
                        if (atomString.contains(","))
                            atomString = atomString.substring(atomString.indexOf(','));
                         else 
                            atomString = ")";
                        
                        partofAtom = partofAtom + "*" + atomString;
                        Vector<GroundAtom> foo = getGAforBlock(partofAtom); 
                        vars.addBlock(foo);
                        atoms.removeAll(foo);;
                        break;
                    }
                    partofAtom = partofAtom + atomString.substring(0, atomString.indexOf(',') + 1);
                    atomString = atomString.substring(atomString.indexOf(',') + 1);
                }
            }
        }
    }
    
	/**
     * retrieves the variable (ground atom) containing all given String and at position of the asterisk (must be given) any additional String (before, between or afterwards)
     * @param gndAtom
     * @return
     */
    public Vector<GroundAtom> getGAforBlock(String gndAtom){
        Vector<GroundAtom> block = new Vector<GroundAtom>();
        if (gndAtom.contains("*")){
            String part1 = gndAtom.substring(0, gndAtom.indexOf("*"));
            String part2 = gndAtom.substring(gndAtom.indexOf("*")+1);
            for (String atomString : vars.getVariableStrings()){
                if (atomString.contains(part1) && atomString.contains(part2)){
                    block.add(vars.get(atomString));
                }
            }
        }
        return block;
    }
    
    /**
     * creates groundings for all formulas
     * @param makelist boolean (if true the grounded formula will be saved in a set)
     * @param gc callback method (if not null, the callback method is called for each grounded formula)
     * @throws Exception 
     */
    protected void groundFormulas(boolean makelist, GroundingCallback gc) throws Exception {
        weightedFormulas = new Vector<WeightedFormula>();
        for(Formula form : mln.formulas) {
        	double weight = mln.formula2weight.get(form);
        	boolean isHard = weight == mln.getMaxWeight();
            for(Formula gf : form.getAllGroundings(db, vars, true)) {
                if(makelist)
                    weightedFormulas.add(new WeightedFormula(gf, weight, isHard));
                if(gc != null)
                    gc.onGroundedFormula(gf, weight, db);
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
}