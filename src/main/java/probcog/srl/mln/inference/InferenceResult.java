/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.mln.inference;

import probcog.logic.GroundAtom;

/**
 * Represents an inference result.
 * @author Dominik Jain
 */
public class InferenceResult implements Comparable<InferenceResult> {
	public GroundAtom ga;
	public double value;
	
	public InferenceResult(GroundAtom ga, double value) {
		this.value = value;	
		this.ga = ga;
	}
	
	public void print() {
		System.out.println(toString());
	}

	public int compareTo(InferenceResult o) {
		return this.ga.toString().compareTo(o.ga.toString());
	}
	
	@Override
	public String toString() {
		return String.format("  %.4f  %s", value, ga.toString());
	}
}
