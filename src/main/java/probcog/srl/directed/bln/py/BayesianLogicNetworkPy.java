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

import java.util.Properties;

import org.python.core.PyObject.ConversionException;
import org.python.util.PythonInterpreter;

import probcog.exception.ProbCogException;
import probcog.srl.Database;
import probcog.srl.directed.RelationalBeliefNetwork;
import probcog.srl.directed.bln.AbstractBayesianLogicNetwork;
import probcog.tools.JythonInterpreter;


/**
 * Bayesian Logic Network with Python-based logic implementation
 * @author Dominik Jain
 * @deprecated
 */
public class BayesianLogicNetworkPy extends AbstractBayesianLogicNetwork {
	public RelationalBeliefNetwork rbn;
	public String logicFile;
	public JythonInterpreter jython;
	protected State state;
	
	public BayesianLogicNetworkPy(String declsFile, String networkFile, String logicFile) throws ProbCogException {
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
	public GroundBLN ground(Database db) throws ProbCogException {
		return new GroundBLN(this, db);
	}

	@Override
	protected void initKB() throws ProbCogException {
		System.out.println("loading logic network...");
		jython.exec("mln = MLN('%s')", logicFile);
	}
	
	@Override
	protected void addLogicalConstraint(String s) throws UnsupportedOperationException {
		throw new UnsupportedOperationException();
	}
}
