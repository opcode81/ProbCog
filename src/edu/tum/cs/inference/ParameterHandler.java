/*
 * Created on Nov 17, 2009
 */
package edu.tum.cs.inference;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import edu.tum.cs.util.StringTool;

public class ParameterHandler {
	/**
	 * maps parameter names to ParameterMapping objects that can apply them
	 */
	protected java.util.HashMap<String, ParameterMapping> mappings;
	protected Object owner;
	protected Vector<ParameterHandler> subhandlers;
	protected Vector<ParameterHandler> parenthandlers;
	protected Map<String,Object> submittedParams = null;
	protected HashSet<String> unhandledParams = new HashSet<String>();
	
	public ParameterHandler(IParameterHandler owner) {
		mappings = new HashMap<String, ParameterMapping>();
		subhandlers = new Vector<ParameterHandler>();
		parenthandlers = new Vector<ParameterHandler>();
		this.owner = owner;
	}
	
	/**
	 * 
	 * @param paramName
	 * @param setterMethodName
	 * @throws Exception 
	 */
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
		if(h == null)
			throw new Exception("Subhandler cannot be null");
		subhandlers.add(h);
		h.parenthandlers.add(this);
		// if parameters were previously submitted, pass them on to the new subhandler;
		// if previously unhandled parameters can be handled, parent handlers will be notified 
		if(submittedParams != null)
			h.handle(submittedParams, false);
	}
	
	public void addSubhandler(IParameterHandler h) throws Exception {
		addSubhandler(h.getParameterHandler());
	}
	
	/**
	 * handles all of the parameters given in a parameter mapping
	 * @param paramMapping a mapping from parameter names to values
	 * @param exceptionIfUnhandledParam whether to throw an exception if a parameter cannot be handled now. Note that the parameter handling scheme will try to handle parameters later on, should new subhandlers be added after handle() is called
	 * @throws Exception
	 */
	public void handle(Map<String, Object> paramMapping, boolean exceptionIfUnhandledParam) throws Exception {		
		submittedParams = paramMapping;
		for(Entry<String,Object> param : paramMapping.entrySet()) {
			handle(param.getKey(), param.getValue());
		}
		if(exceptionIfUnhandledParam && !unhandledParams.isEmpty())
			throw new Exception("Parameters " + StringTool.join(", ", unhandledParams) + " unhandled! Known parameters: " + this.getHandledParameters().toString());
	}
	
	public Collection<String> getUnhandledParams() {
		return unhandledParams; 
	}
	
	protected boolean handle(String paramName, Object value) throws Exception {
		// try to handle here
		ParameterMapping m = mappings.get(paramName);
		boolean handled = false;
		if(m != null) {
			m.apply(value);
			handled = true;
		}
		// pass parameter on to subhandlers
		for(ParameterHandler h : subhandlers)
			if(h.handle(paramName, value))
				handled = true;
		// if the parameter could be handled, notify
		if(handled)
			onHandled(paramName);
		// otherwise store as unhandled
		else
			unhandledParams.add(paramName);
		return handled;
	}
	
	protected void onHandled(String paramName) {
		if(unhandledParams.contains(paramName))
			unhandledParams.remove(paramName);
		// notify parents of handling
		for(ParameterHandler h : this.parenthandlers)
			h.onHandled(paramName);
	}
	
	/**
	 * gets the names of all parameters that can be handled
	 * @return
	 */
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
	
	public void printHelp(PrintStream out) {
		if(!mappings.isEmpty()) {
			out.println("handled by " + owner.getClass().getSimpleName() + ":");
			for(Entry<String,ParameterMapping> e : this.mappings.entrySet()) {
				Class<?> paramType = e.getValue().setterMethod.getParameterTypes()[0];
				System.out.printf("  --%s=%s\n", e.getKey(), paramType.getSimpleName());
			}
		}
		for(ParameterHandler h : subhandlers)
			h.printHelp(out);
	}
	
	protected class ParameterMapping {
		protected Method setterMethod;		
		
		public ParameterMapping(Method setterMethod) {
			this.setterMethod = setterMethod;
		}
		
		public void apply(Object value) throws Exception {			
			Class<?> paramType = setterMethod.getParameterTypes()[0];
			if(value instanceof String) {
				String strValue = (String)value;
				if(paramType == Double.class || paramType == double.class) {
					setterMethod.invoke(owner, Double.parseDouble(strValue));
					return;
				}
				if(paramType == Integer.class || paramType == int.class) {
					setterMethod.invoke(owner, Integer.parseInt(strValue));
					return;
				}
				if(paramType == Boolean.class || paramType == boolean.class) {
					setterMethod.invoke(owner, Boolean.parseBoolean(strValue));
					return;
				}
				if(paramType == String.class) {
					setterMethod.invoke(owner, strValue);
					return;
				}
				throw new Exception("Don't know how to handle setter argument of type " + paramType.getCanonicalName() + " for " + setterMethod.getName() + "; allowed types are: Double, Integer, String, Boolean");
			}
			else
				setterMethod.invoke(owner, value);			
		}
	}
}
