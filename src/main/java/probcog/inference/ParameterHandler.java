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
package probcog.inference;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import edu.tum.cs.util.StringTool;
import probcog.exception.ProbCogException;

/**
 * Class to support the dynamic handling of parameters by name.
 * @author Dominik Jain
 */
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
	 * Adds a handled parameter
	 * @param paramName the name of the parameter
	 * @param setterMethodName the name of the setter method
	 * @param description parameter description
	 * @throws ProbCogException 
	 */
	public void add(String paramName, String setterMethodName, String description) throws ProbCogException {
		for(Method m : owner.getClass().getMethods()) {
			if(m.getName().equals(setterMethodName)) {
				Class<?>[] paramTypes = m.getParameterTypes();
				if(paramTypes.length != 1)
					continue;
				mappings.put(paramName, new ParameterMapping(m, description));				
				return;
			}
		}
		throw new ProbCogException("Could not find an appropriate setter method with 1 parameter in class " + owner.getClass().getName());
	}
	
	/**
	 * Adds a handled parameter
	 * @param paramName the name of the parameter
	 * @param setterMethodName the name of the setter method
	 * @throws ProbCogException 
	 */
	public void add(String paramName, String setterMethodName) throws ProbCogException {
		add(paramName, setterMethodName, null);
	}
	
	public void addSubhandler(ParameterHandler h) throws ProbCogException {
		if(h == null)
			throw new IllegalArgumentException("Subhandler cannot be null");
		subhandlers.add(h);
		h.parenthandlers.add(this);
		// if parameters were previously submitted, pass them on to the new subhandler;
		// if previously unhandled parameters can be handled, parent handlers will be notified 
		if(submittedParams != null)
			h.handle(submittedParams, false);
	}
	
	public void addSubhandler(IParameterHandler h) throws ProbCogException {
		addSubhandler(h.getParameterHandler());
	}
	
	/**
	 * handles all of the parameters given in a parameter mapping
	 * @param paramMapping a mapping from parameter names to values
	 * @param exceptionIfUnhandledParam whether to throw an exception if a parameter cannot be handled now. Note that the parameter handling scheme will try to handle parameters later on, should new subhandlers be added after handle() is called
	 * @throws ProbCogException
	 */
	public void handle(Map<String, Object> paramMapping, boolean exceptionIfUnhandledParam) throws ProbCogException {		
		submittedParams = paramMapping;
		for(Entry<String,Object> param : paramMapping.entrySet()) {
			handle(param.getKey(), param.getValue());
		}
		if(exceptionIfUnhandledParam && !unhandledParams.isEmpty())
			throw new ProbCogException("Parameters " + StringTool.join(", ", unhandledParams) + " unhandled! Known parameters: " + this.getHandledParameters().toString());
	}
	
	public Collection<String> getUnhandledParams() {
		return unhandledParams; 
	}
	
	protected boolean handle(String paramName, Object value) throws ProbCogException {
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
		ret.addAll(mappings.keySet());
		for(ParameterHandler h : subhandlers)
			h.getHandledParameters(ret);
	}
	
	public boolean isSupportedParameter(String paramName) {
		if (mappings.containsKey(paramName))
			return true;
		for(ParameterHandler h : subhandlers)
			if (h.isSupportedParameter(paramName))
				return true;
		return false;
	}
	
	/**
	 * Prints help on handled parameters
	 * @param out the stream to write to
	 * @param argFormat if true, use the command-line argument style "--param=type (description)" instead of "param: type (description)"
	 */
	public void printHelp(PrintStream out, boolean argFormat) {
		String formatString = argFormat ? "  --%s=%s%s\n" : "  %s: %s%s\n";
		if(!mappings.isEmpty()) {
			out.println("handled by " + owner.getClass().getSimpleName() + ":");
			for(Entry<String,ParameterMapping> e : this.mappings.entrySet()) {
				Class<?> paramType = e.getValue().setterMethod.getParameterTypes()[0];
				String paramDescription = e.getValue().paramDescription;
				out.printf(formatString, e.getKey(), 
						paramType.getPackage() == null ? paramType.getSimpleName() : paramType.getName(),
						paramDescription != null ? " (" + paramDescription + ")" : "");
			}
		}
		for(ParameterHandler h : subhandlers)
			h.printHelp(out, argFormat);
	}
	
	protected class ParameterMapping {
		protected Method setterMethod;
		private String paramDescription;		
		
		public ParameterMapping(Method setterMethod, String paramDescription) {
			this.setterMethod = setterMethod;
			this.paramDescription = paramDescription;
		}
		
		public void apply(Object value) throws ProbCogException {	
			try {
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
					throw new ProbCogException("Don't know how to handle setter argument of type " + paramType.getCanonicalName() + " for " + setterMethod.getName() + "; allowed types are: Double, Integer, String, Boolean");
				}
				else
					setterMethod.invoke(owner, value);
			}
			catch (InvocationTargetException|IllegalAccessException e) {
				throw new ProbCogException("Error calling setter method " + setterMethod, e);
			}
		}
	}
}
