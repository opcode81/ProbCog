/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.tum.cs.srl.mln;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.ParameterGrounder;
import edu.tum.cs.srl.Signature;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that represents a grounded instance of a MLN-file
 * @author wernickr
 */
public class MarkovRandomField {
    protected Database db;
    public MarkovLogicNetwork mln;
    protected String dbFile;
    protected HashMap<Formula, Double> groundedFormulas;
    protected WorldVariables world;
    
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
        this.world = new WorldVariables();
        this.mln = mln;
        this.dbFile = dbFileLoc;
        readDB();
        ground();
        createBlocks();
        groundAndSimplify(makelist, gc);
    } 
    
    /**
     * Method that returns worldVariables of the given MLN
     * @return
     */
    public WorldVariables getWorldVariables() {
        return world;
    }
    
    /**
     * Method that parses a given evidence file
     */
    public void readDB(){
        try {
            db.readMLNDB(dbFile);
        } catch (Exception ex) {
            Logger.getLogger(MarkovLogicNetwork.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Method grounds the mln file based on the given evidence
     * creates groundatoms for all signatures
     */
    public void ground() {
        try {
            Collection<Signature> signa = mln.signatures.values();
            for (Signature pre : signa) {
                Collection<String[]> col = ParameterGrounder.generateGroundings(mln.getSignature(pre.functionName), db);
                for (String[] grAtom : col) {
                    GroundAtom gnd = new GroundAtom(pre.functionName, grAtom);
                    world.add(gnd);
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(MarkovLogicNetwork.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Method creates blocks for groundatoms (only for atoms that are block variables)
     */
    public void createBlocks(){
        for (String key : mln.block.keySet()) {
            String[] domains = mln.getSignature(key).argTypes;
            int index = mln.getPosinArray(mln.block.get(key), domains);
            Vector<GroundAtom> atoms = new Vector<GroundAtom>();
            atoms = world.getGAforBlock(key + "*");
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
                        world.addBlock(world.getGAforBlock(partofAtom));
                        atoms.removeAll(world.getGAforBlock(partofAtom));;
                        break;
                    }
                    partofAtom = partofAtom + atomString.substring(0, atomString.indexOf(',') + 1);
                    atomString = atomString.substring(atomString.indexOf(',') + 1);
                }
            }
        }
    }
    
    /**
     * Method that creates grounding for all formulas
     * saves the simplified formulas in a HashMap<Formula, weight>
     */
    public void formulaGrounding(){
        for (Formula form : mln.formulas){
            try {
                for (Formula gf : form.getAllGroundings(db, world)) {
                    groundedFormulas.put(gf, mln.formula2weight.get(form));
                }
            } catch (Exception ex) {
                Logger.getLogger(MarkovRandomField.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Method that creates grounding for all formulas
     * @param makelist boolean (if true the grounded formula will be saved in a set)
     * @param gc callback method (if not null, the callback method is called for each grounded formula)
     */
    public void groundAndSimplify(boolean makelist, GroundingCallback gc) {
        groundedFormulas = new HashMap<Formula, Double>();
        for (Formula form : mln.formulas) {
            try {
                for (Formula gf : form.getAllSimplifiedGroundings(db, world)) {
                    if (makelist)
                        groundedFormulas.put(gf, mln.formula2weight.get(form));
                    if (gc != null)
                        gc.onGroundedFormula(gf, mln.formula2weight.get(form), db);
                }
            } catch (Exception ex) {
                Logger.getLogger(MarkovRandomField.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     * Method returns set of grounded formulas
     * @return
     */
    public HashMap<Formula, Double> getGroundedFormulas() {
        return groundedFormulas;
    }

    /**
     * Method returns database
     * @return
     */
    public Database getDb() {
        return db;
    }
    
    
}