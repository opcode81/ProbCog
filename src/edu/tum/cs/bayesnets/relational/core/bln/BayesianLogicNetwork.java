package edu.tum.cs.bayesnets.relational.core.bln;

import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import org.python.core.PyObject;
import org.python.core.PyObject.ConversionException;
import org.python.util.PythonInterpreter;

import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;
import edu.tum.cs.tools.JythonInterpreter;

public class BayesianLogicNetwork {
	public RelationalBeliefNetwork rbn;
	public String logicFile;
	protected JythonInterpreter jython;
	protected State state;
	
	public BayesianLogicNetwork(RelationalBeliefNetwork rbn, String logicFile) {
		this.rbn = rbn;
		this.logicFile = logicFile;
		state = null;
		
		// initialize jython interpreter
		System.out.println("initializing interpreter...");
		Properties props = new Properties();
		props.put("python.path", ".:/usr/wiss/jain/work/code/SRLDB/bin:/usr/wiss/jain/work/code/SRLDB/datagen:/usr/wiss/jain/work/code/SRLDB/mln");
		PythonInterpreter.initialize(System.getProperties(), props, null);
		jython = new JythonInterpreter();
		
		// load MLN 
		System.out.println("importing libraries...");
		jython.exec("from MLN import MLN");
		System.out.println("loading logic network...");
		jython.exec("mln = MLN('%s')", logicFile);
	}
	
	public void generateGroundFormulas(String domainFile) {
		jython.exec("mln.combineDB('%s')", domainFile);		
		state = new State(jython);
	}
	
	public State getState() {
		return state;
	}
	
	public GroundFormulaIteration iterGroundFormulas() throws ConversionException {
		return new GroundFormulaIteration(this);
	}
}
