package edu.tum.cs.srl.bayesnets.bln.py;

import java.util.Properties;

import org.python.core.PyObject.ConversionException;
import org.python.util.PythonInterpreter;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.bln.AbstractBayesianLogicNetwork;
import edu.tum.cs.tools.JythonInterpreter;

/**
 * Bayesian Logic Network with Python-based logic implementation
 * @author jain
 *
 */
public class BayesianLogicNetworkPy extends AbstractBayesianLogicNetwork {
	public RelationalBeliefNetwork rbn;
	public String logicFile;
	public JythonInterpreter jython;
	protected State state;
	
	public BayesianLogicNetworkPy(String declsFile, String networkFile, String logicFile) throws Exception {
		super(declsFile, networkFile, logicFile);
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

	@Override
	public GroundBLN ground(Database db) throws Exception {
		return new GroundBLN(this, db);
	}

	@Override
	protected void initKB() throws Exception {
		System.out.println("loading logic network...");
		jython.exec("mln = MLN('%s')", logicFile);
	}
}
