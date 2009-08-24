/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.tum.cs.srl.mln;

import edu.tum.cs.logic.Formula;



/**
 *
 * @author msgpool
 */
public interface GroundingCallback { 
    
    public void onGroundedFormula(Formula f, double weight, MarkovRandomField mrf);
    
}
