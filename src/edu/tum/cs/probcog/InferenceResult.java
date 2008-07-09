package edu.tum.cs.probcog;

public class InferenceResult {
	public String functionName;
	public String[] params;
	public double probability;
	
	public InferenceResult(String functionName, String[] params, double p) {
		this.functionName = functionName;
		this.params = params;
		this.probability = p;
	}
}
