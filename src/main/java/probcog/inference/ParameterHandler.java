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
	protected java.util.HashMap<String, Parameter> parameters;
	protected Object owner;
	protected Vector<ParameterHandler> subhandlers;
	protected Vector<ParameterHandler> parenthandlers;
	protected Map<String,Object> submittedParams = null;
	protected HashSet<String> unhandledParams = new HashSet<String>();
	
	public ParameterHandler(IParameterHandler owner) {
		parameters = new HashMap<String, Parameter>();
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
				parameters.put(paramName, new ParameterWithSetterMethod(m, description));				
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
	
	public interface Setter<T> {
		void setValue(T value);
	}
	
	public <T> void add(String paramName, Class<T> parameterValueClass, Setter<T> lambdaSetter, String description) {
		parameters.put(paramName, new ParameterWithLambdaSetter<T>(lambdaSetter, parameterValueClass, description));
	}
	
	public <T> void add(String paramName, Class<T> parameterValueClass, Setter<T> lambdaSetter) {
		add(paramName, parameterValueClass, lambdaSetter, null);
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
			throw new ProbCogException("Parameters " + StringTool.join(", ", unhandledParams) + " unhandled! Known parameters: " + this.getSupportedParameters().toString());
	}
	
	public Collection<String> getUnhandledParams() {
		return unhandledParams; 
	}
	
	protected boolean handle(String paramName, Object value) throws ProbCogException {
		// try to handle here
		Parameter m = parameters.get(paramName);
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
	public Vector<String> getSupportedParameters() {
		Vector<String> ret = new Vector<String>();
		getSupportedParameters(ret);
		return ret; 
	}
	
	protected void getSupportedParameters(Vector<String> ret) {
		ret.addAll(parameters.keySet());
		for(ParameterHandler h : subhandlers)
			h.getSupportedParameters(ret);
	}
	
	public boolean isSupportedParameter(String paramName) {
		if (parameters.containsKey(paramName))
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
		if(!parameters.isEmpty()) {
			out.println("handled by " + owner.getClass().getSimpleName() + ":");
			for(Entry<String,Parameter> e : this.parameters.entrySet()) {
				Class<?> paramType = e.getValue().getParameterValueClass();
				String paramDescription = e.getValue().getDescription();
				out.printf(formatString, e.getKey(), 
						paramType.getPackage() == null ? paramType.getSimpleName() : paramType.getName(),
						paramDescription != null ? " (" + paramDescription + ")" : "");
			}
		}
		for(ParameterHandler h : subhandlers)
			h.printHelp(out, argFormat);
	}
	
	interface String2Value {
		Object valueFromString(String s);
	}
	
	interface ApplicableClassChecker {
		boolean appliesToClass(Class<?> cls);
	}

	enum ParameterValueType {
		DOUBLE(s -> Double.parseDouble(s), 
				cls -> cls == Double.class || cls == double.class),
		INTEGER(s -> Integer.parseInt(s), 
				cls -> cls == Integer.class || cls == int.class),
		LONG(s -> Long.parseLong(s), 
				cls -> cls == Long.class || cls == long.class),
		BOOLEAN(s -> Boolean.parseBoolean(s), 
				cls -> cls == Boolean.class || cls == boolean.class),
		STRING(s -> s, 
				cls -> cls == String.class),
		OTHER(null, cls -> true);
		
		private String2Value stringConverter;
		private ApplicableClassChecker applicableClassChecker;

		private ParameterValueType(String2Value stringConverter, ApplicableClassChecker applicableClassChecker) {
			this.stringConverter = stringConverter;
			this.applicableClassChecker = applicableClassChecker;
		}
		
		public Object valueFromString(String s) throws ProbCogException {
			if (stringConverter == null) 
				throw new ProbCogException("Cannot convert String to value of type " + this);
			return stringConverter.valueFromString(s);
		}
		
		public static ParameterValueType typeForClass(Class<?> cls) {
			for (ParameterValueType type : ParameterValueType.values()) {
				if (type.applicableClassChecker.appliesToClass(cls))
					return type;
			}
			throw new IllegalStateException("Found no matching type");
		}
	}
	
	protected abstract class Parameter {
		private String description;

		public Parameter(String description) {
			this.description = description;
		}
		
		public abstract void apply(Object value)  throws ProbCogException;
		
		public String getDescription() {
			return description;
		}
		
		public abstract Class<?> getParameterValueClass();
	}
	
	protected class ParameterWithSetterMethod extends Parameter {
		protected Method setterMethod;
		protected ParameterValueType valueType;
		protected Class<?> valueClass;
		
		public ParameterWithSetterMethod(Method setterMethod, String paramDescription) {
			super(paramDescription);
			this.setterMethod = setterMethod;
			this.valueClass = setterMethod.getParameterTypes()[0];
			this.valueType = ParameterValueType.typeForClass(valueClass);
		}
		
		public void apply(Object value) throws ProbCogException {	
			try {
				if(value instanceof String) {
					String strValue = (String)value;
					setterMethod.invoke(owner, valueType.valueFromString(strValue));
				}
				else
					setterMethod.invoke(owner, value);
			}
			catch (InvocationTargetException|IllegalAccessException e) {
				throw new ProbCogException("Error calling setter method " + setterMethod, e);
			}
		}

		@Override
		public Class<?> getParameterValueClass() {
			return valueClass;
		}
	}
	
	protected class ParameterWithLambdaSetter<T> extends Parameter {
		protected Setter<T> setter;
		protected ParameterValueType valueType;
		protected Class<T> valueClass;
		
		public ParameterWithLambdaSetter(Setter<T> setter, Class<T> cls, String paramDescription) {
			super(paramDescription);
			this.setter = setter;
			this.valueClass = cls;
			this.valueType = ParameterValueType.typeForClass(valueClass);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public void apply(Object value) throws ProbCogException {	
			if(value instanceof String) {
				String strValue = (String)value;
				setter.setValue((T)valueType.valueFromString(strValue));
			}
			else
				setter.setValue((T)value);
		}

		@Override
		public Class<?> getParameterValueClass() {
			return valueClass;
		}
	}
}
