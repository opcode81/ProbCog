package edu.tum.cs.bayesnets.relational.core.bln.py;

import org.python.core.PyObject.ConversionException;

import edu.tum.cs.tools.JythonInterpreter;

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
