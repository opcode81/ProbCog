/*
 * Created on Nov 17, 2009
 */
package edu.tum.cs.inference;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;

public class ParameterHandler {
	/**
	 * maps parameter names to ParameterMapping objects that can apply them
	 */
	protected java.util.HashMap<String, ParameterMapping> mappings;
	protected Object owner;
	protected Vector<ParameterHandler> subhandlers;
	protected Vector<ParameterHandler> parenthandlers;
	protected Map<String,String> submittedParams = null;
	protected HashSet<String> unhandledParams = new HashSet<String>();
	
	public ParameterHandler(IParameterHandler owner) {
		mappings = new HashMap<String, ParameterMapping>();
		subhandlers = new Vector<ParameterHandler>();
		parenthandlers = new Vector<ParameterHandler>();
		this.owner = owner;
	}
	
	public void add(String paramName, String setterMethodName) throws Exception {
		for(Method m : owner.getClass().getMethods()) {
			if(m.getName().equals(setterMethodName)) {
				Class<?>[] paramTypes = m.getParameterTypes();
				if(paramTypes.length != 1)
					continue;
				mappings.put(paramName, new ParameterMapping(m));				
				return;
			}
		}
		throw new Exception("Could not find an appropriate setter method with 1 parameter in class " + owner.getClass().getName());
	}
	
	public void addSubhandler(ParameterHandler h) throws Exception {		
		subhandlers.add(h);
		h.parenthandlers.add(this);
		onAddedSubhandler(h);
	}
	
	public void onAddedSubhandler(ParameterHandler h) throws Exception {
		// see if the new subhandler allows us to handle unhandled params
		if(!unhandledParams.isEmpty())
			handle(submittedParams, unhandledParams, false);		
		// notify parents of addition of subhandler
		for(ParameterHandler parent : this.parenthandlers)
			parent.onAddedSubhandler(h);		
	}
	
	public void handle(Map<String, String> paramMapping, Collection<String> paramsToHandle, boolean exceptionIfUnhandledParam) throws Exception {		
		for(String param : paramsToHandle) {
			String value = paramMapping.get(param);
			if(handle(param, value)) {
				if(unhandledParams.contains(param))
					unhandledParams.remove(param);
			}
			else {
				if(exceptionIfUnhandledParam)
					throw new Exception("Parameter " + param + " unhandled! Known parameters: " + this.getHandledParameters().toString());
				else {
					unhandledParams.add(param);
				}
			}
		}
	}
	
	/**
	 * 
	 * @param paramMapping
	 * @param exceptionIfUnhandledParam whether to throw an exception if a parameter cannot be handled now. Note that the parameter handling scheme will try to handle parameters later on, should new subhandlers be added after handle() is called
	 * @throws Exception
	 */
	public void handle(Map<String, String> paramMapping, boolean exceptionIfUnhandledParam) throws Exception {
		submittedParams = paramMapping;
		handle(paramMapping, paramMapping.keySet(), exceptionIfUnhandledParam);
	}
	
	public Collection<String> getUnhandledParams() {
		return unhandledParams; 
	}
	
	protected boolean handle(String paramName, String value) throws Exception {
		ParameterMapping m = mappings.get(paramName);
		boolean handled = false;
		if(m != null) {
			m.apply(value);
			handled = true;
		}
		for(ParameterHandler h : subhandlers)
			if(h.handle(paramName, value))
				handled = true;
		return handled;
	}
	
	public Vector<String> getHandledParameters() {
		Vector<String> ret = new Vector<String>();
		getHandledParameters(ret);
		return ret; 
	}
	
	protected void getHandledParameters(Vector<String> ret) {
		for(String param : mappings.keySet())
			ret.add(param);
		for(ParameterHandler h : subhandlers)
			h.getHandledParameters(ret);
	}
	
	protected class ParameterMapping {
		protected Method setterMethod;		
		
		public ParameterMapping(Method setterMethod) {
			this.setterMethod = setterMethod;
		}
		
		public void apply(String value) throws Exception {
			Class<?> paramType = setterMethod.getParameterTypes()[0];
			if(paramType == Double.class || paramType == double.class) {
				setterMethod.invoke(owner, Double.parseDouble(value));
				return;
			}
			if(paramType == Integer.class || paramType == int.class) {
				setterMethod.invoke(owner, Integer.parseInt(value));
				return;
			}
			if(paramType == Boolean.class || paramType == boolean.class) {
				setterMethod.invoke(owner, Boolean.parseBoolean(value));
				return;
			}
			if(paramType == String.class) {
				setterMethod.invoke(owner, value);
				return;
			}
			throw new Exception("Don't know how to handle setter argument of type " + paramType.getCanonicalName() + " for " + setterMethod.getName() + "; allowed types are: Double, Integer, String, Boolean");
		}
	}
}
