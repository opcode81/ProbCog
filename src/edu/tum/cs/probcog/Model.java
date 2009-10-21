package edu.tum.cs.probcog;

import java.util.HashMap;
import java.util.Vector;
import java.util.Map.Entry;

public abstract class Model {
	private HashMap<String,String> parameters;
	/**
	 * maps from ProbCog constants to external constants
	 */
	public HashMap<String,String> constantMapFromProbCog;
	/**
	 * maps from external constants to ProbCog constants
	 */
	public HashMap<String,String> constantMapToProbCog;
	/**
	 * name of the model
	 */
	protected String name;
	
	public Model(String name) {
		parameters = new HashMap<String,String>();
		this.name = name;
		constantMapFromProbCog = null;
	}
	protected abstract void _setEvidence(Iterable<String[]> evidence) throws Exception;
	public abstract void instantiate() throws Exception;
	/**
	 * runs the actual inference method, without mapping constants  
	 * @param queries
	 * @return
	 * @throws Exception
	 */
	protected abstract java.util.Vector<InferenceResult> _infer(Iterable<String> queries) throws Exception;
	public abstract Vector<String[]> getPredicates();
	public abstract Vector<String[]> getDomains();

	public void setEvidence(Iterable<String[]> evidence) throws Exception {
		for(String[] s : evidence) {
			for(int i = 1; i < s.length; i++)
				s[i] = this.mapConstantToProbCog(s[i]);
		}
		_setEvidence(evidence);
	}
	
	public java.util.Vector<InferenceResult> infer(Iterable<String> queries) throws Exception {
		// run inference
		Vector<InferenceResult> actualResults = _infer(queries);
		// map results and return
		Vector<InferenceResult> mappedResults = new Vector<InferenceResult>(); 
		for(InferenceResult r : actualResults) {
			if(!r.mapConstants(this))
				continue;
			mappedResults.add(r);
		}
		return mappedResults;
	}
	
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
	
	/**
	 * sets the mapping from ProbCog constants to external constants
	 * @param constantMap
	 */
	public void setConstantMap(HashMap<String,String> constantMap) {
		this.constantMapFromProbCog = constantMap;
		// create inverse mapping
		constantMapToProbCog = new HashMap<String,String>();
		for(Entry<String, String> e : constantMapFromProbCog.entrySet())
			constantMapToProbCog.put(e.getValue(), e.getKey());
	}	
	
	/**
	 * maps a ProbCog constant to an external constant
	 * @param c
	 * @return
	 */
	public String mapConstantFromProbCog(String c) {
		if(constantMapFromProbCog == null)
			return c;
		String c2 = constantMapFromProbCog.get(c);
		if(c2 == null)
			return c;
		if(c2.length() == 0)
			return null;
		return c2;
	}
	
	/**
	 * maps an external constant to a ProbCog constant
	 * @param c
	 * @return
	 */
	public String mapConstantToProbCog(String c) {
		if(constantMapToProbCog == null)
			return c;
		String c2 = constantMapToProbCog.get(c);
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
