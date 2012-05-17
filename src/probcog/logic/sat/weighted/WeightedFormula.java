/*
 * Created on Aug 10, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.logic.sat.weighted;

import probcog.logic.Formula;

public class WeightedFormula {
	public Formula formula;	
	public double weight;
	public boolean isHard;
	
	public WeightedFormula(Formula f, double weight, boolean isHard) {
		this.formula = f;
		this.weight = weight;
		this.isHard = isHard;
	}
	
	public String toString() {
		if(isHard)
			return formula.toString() + ".";
		else
			return weight + " " + formula.toString();
	}
}
