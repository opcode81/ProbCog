/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.tum.cs.srl.mln;

import edu.tum.cs.logic.sat.weighted.WeightedFormula;



/**
 *
 * @author msgpool
 */
public interface GroundingCallback { 
    
    public void onGroundedFormula(WeightedFormula wf, MarkovRandomField mrf) throws Exception;
    
}
