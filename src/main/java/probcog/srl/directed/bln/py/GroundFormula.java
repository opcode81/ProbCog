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

import java.util.Vector;

import org.python.core.PyObject;
import org.python.core.PyObject.ConversionException;

import probcog.tools.JythonInterpreter;


public class GroundFormula {
	protected String varName;
	protected JythonInterpreter jython;
	public int idxGF;
	
	public GroundFormula(JythonInterpreter jython, int idxGF) throws ConversionException {
		this.jython = jython;
		varName = String.format("mln.gndFormulas[%d]", idxGF);
		this.idxGF = idxGF;
		//this.idxF = jython.evalInt("%s.idxFormula", varName);
	}

	public Vector<String> getGroundAtoms() {
		PyObject list = jython.eval("%s.getGroundAtoms()", varName);
		Vector<String> v = new Vector<String>();
		for(int i = 0; i < list.__len__(); i++) {
			v.add(list.__getitem__(i).__str__().toString());
		}
		return v;
	}
	
	public boolean isTrue(State state) throws ConversionException {
		return jython.evalBoolean("%s.isTrue(%s)", varName, state.varName);
	}
	
	public void toCNF() throws ConversionException {
		jython.exec("%s = %s.toCNF()", varName, varName);
		if(jython.evalBoolean("type(%s)==MLN.FOL.Conjunction", varName)) {
			int numChildren = jython.evalInt("len(%s.children)", varName);
			for(int i = 0; i < numChildren; i++) {
				makeClause(String.format("%s.children[%d]", varName, i));
			}				
		}
		else
			makeClause(varName);
	}
	
	protected void makeClause(String cVar) throws ConversionException {
		System.out.println("clause: " + jython.evalString("str(%s)", cVar));
	}
	
	public String toString() {
		try {
			return jython.evalString("str(%s)", varName);
		}
		catch (ConversionException e) {
			e.printStackTrace();
			return null;
		}
	}
}
