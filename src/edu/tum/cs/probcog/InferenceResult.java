package edu.tum.cs.probcog;

import java.io.PrintStream;

import edu.tum.cs.util.StringTool;

public class InferenceResult {
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
}
