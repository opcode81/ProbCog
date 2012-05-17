/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package probcog.srl.mln;

import probcog.logic.sat.weighted.WeightedFormula;



/**
 *
 * @author msgpool
 */
public interface GroundingCallback { 
    
    public void onGroundedFormula(WeightedFormula wf, MarkovRandomField mrf) throws Exception;
    
}
