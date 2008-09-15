package edu.tum.cs.probcog;

import java.util.HashMap;
import java.util.Vector;

public abstract class Model {
	private HashMap<String,String> parameters;
	public HashMap<String,String> constantMap;
	protected String name;
	
	public Model(String name) {
		parameters = new HashMap<String,String>();
		this.name = name;
		constantMap = null;
	}
	public abstract void setEvidence(Iterable<String[]> evidence) throws Exception;
	public abstract void instantiate() throws Exception;
	public abstract java.util.Vector<InferenceResult> infer(Iterable<String> queries) throws Exception;
	public abstract Vector<String[]> getPredicates();
	public abstract Vector<String[]> getDomains();
	
	public String getParameter(String key) {
		return parameters.get(key);
	}
	
	public String getParameter(String key, String defaultValue) {
		String value = parameters.get(key);
		if(value == null)
			return defaultValue;
		return value;
	}
	
	public Integer getIntParameter(String key, Integer defaultValue) {
		return Integer.parseInt(getParameter(key, defaultValue.toString()));
	}
	
	public void setParameter(String key, String value) {
		parameters.put(key, value);
	}
	
	public void setParameters(HashMap<String,String> params) {
		this.parameters = params;
	}
	
	public void setConstantMap(HashMap<String,String> constantMap) {
		this.constantMap = constantMap;
	}
	
	public String mapConstant(String c) {
		if(constantMap == null)
			return c;
		String c2 = constantMap.get(c);
		if(c2 == null)
			return c;
		if(c2.length() == 0)
			return null;
		return c2;
	}
	
	public String getName() {
		return name;
	}
}
