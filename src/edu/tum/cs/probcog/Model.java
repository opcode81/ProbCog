package edu.tum.cs.probcog;

import java.util.HashMap;

public abstract class Model {
	private HashMap<String,String> parameters;
	protected String name;
	
	public Model(String name) {
		parameters = new HashMap<String,String>();
		this.name = name;
	}
	public abstract void setEvidence(Iterable<String[]> evidence) throws Exception;
	public abstract void instantiate() throws Exception;
	public abstract java.util.Vector<InferenceResult> infer(Iterable<String> queries) throws Exception;
	
	protected String getParameter(String key) {
		return parameters.get(key);
	}
	
	protected String getParameter(String key, String defaultValue) {
		String value = parameters.get(key);
		if(value == null)
			return defaultValue;
		return value;
	}
	
	protected Integer getIntParameter(String key, Integer defaultValue) {
		return Integer.parseInt(getParameter(key, defaultValue.toString()));
	}
	
	protected void setParameter(String key, String value) {
		parameters.put(key, value);
	}
	
	public String getName() {
		return name;
	}
}
