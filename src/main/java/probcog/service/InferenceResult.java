/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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

package probcog.service;

import java.io.PrintStream;

import edu.tum.cs.util.StringTool;

/**
 * Represents an inference result in the ProbCog service.
 * @author Dominik Jain
 */
public class InferenceResult implements Comparable<InferenceResult> {
	public String functionName;
	public String[] params;
	public double probability;
	
	public InferenceResult(String functionName, String[] params, double p) {
		this.functionName = functionName;
		this.params = params;
		this.probability = p;
	}
	
	/**
	 * maps constants in the inference result to constants used by the external system
	 * @param m the model whose mapping to use
	 * @return false if one of the constants was mapped to nothing (i.e. unsupported by receiving system), true otherwise
	 */
	public boolean mapConstants(Model m) {
		//String[] mappedParams = new String[params.length];
		for(int i = 0; i < params.length; i++) {
			params[i] = m.mapConstantFromProbCog(params[i]);
			if(params[i] == null)
				return false;			
		}
		return true;
	}
	
	public String toString() {
		return String.format("%.6f  %s(%s)", probability, functionName, StringTool.join(", ", params));
	}
	
	public void print(PrintStream out) {
		out.println(this);
	}

	@Override
	public int compareTo(InferenceResult arg0) {
		return Double.compare(this.probability, arg0.probability);
	}	
}
