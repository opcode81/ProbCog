package edu.tum.cs.tools;

import java.util.Formatter;

import org.python.core.PyObject;
import org.python.core.PyObject.ConversionException;
import org.python.util.PythonInterpreter;

public class JythonInterpreter extends PythonInterpreter {
	public JythonInterpreter() {
		super();
	}
	
    public void exec(String command, Object ... args) {
    	command = new Formatter().format(command, args).toString();
    	this.exec(command);
    }
    
    public PyObject eval(String command, Object ... args) {
    	command = new Formatter().format(command, args).toString();
    	return this.eval(command);
    }
    
    public int evalInt(String command, Object ... args) throws ConversionException {
    	return eval(command, args).asInt(0);
    }
    
    public boolean evalBoolean(String command, Object ... args) throws ConversionException {
    	return evalInt(command, args) == 1;
    }

    public String evalString(String command, Object ... args) throws ConversionException {
    	return eval(command, args).toString();
    }
    
    public double evalDouble(String command, Object ... args) throws NumberFormatException, ConversionException {
    	return Double.parseDouble(evalString(command, args));
    }
}
