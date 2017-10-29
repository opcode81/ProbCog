/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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
package probcog.tools;

import java.util.Formatter;

import org.python.core.PyObject;
import org.python.core.PyObject.ConversionException;
import org.python.util.PythonInterpreter;

/**
 * @author Dominik Jain
 */
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
