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
	
	public boolean mapConstants(Model m) {
		//String[] mappedParams = new String[params.length];
		for(int i = 0; i < params.length; i++) {
			params[i] = m.mapConstant(params[i]);
			if(params[i] == null)
				return false;			
		}
		return true;
	}
}
