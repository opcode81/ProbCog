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
package probcog.srl.directed.bln.py;

import org.python.core.PyObject.ConversionException;

import probcog.tools.JythonInterpreter;


public class State {
	JythonInterpreter jython;
	String varName;
	
	public State(JythonInterpreter jython) {
		this.jython = jython;
		varName = "state";
		jython.exec("%s = [None for i in range(len(mln.gndAtoms))]", varName);
	}

	public void set(String gndAtom, boolean value) throws ConversionException {
		int idxGA = jython.evalInt("mln.gndAtoms['%s'].idx", gndAtom);
		jython.exec("%s[%d] = %s", varName, idxGA, value ? "True" : "False");
	}
	
	public boolean get(String gndAtom) throws ConversionException {
		return jython.evalBoolean("state[mln.gndAtoms['%s'].idx]", gndAtom);
	}
}
