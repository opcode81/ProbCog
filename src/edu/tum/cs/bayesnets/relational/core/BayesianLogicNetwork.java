package edu.tum.cs.bayesnets.relational.core;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import org.python.core.PyObject;
import org.python.core.PyObject.ConversionException;
import org.python.util.PythonInterpreter;

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
		System.out.println("loading MLN...");
		jython.exec("mln = MLN('%s')", logicFile);
	}
	
	public void generateGroundFormulas(String domainFile) {
		jython.exec("mln.combineDB('%s')", domainFile);		
		state = new State(jython);
	}
	
	public State getState() {
		return state;
	}
	
	public Iterable<GroundFormula> iterGroundFormulas() throws ConversionException {
		return new GroundFormulaIteration(this);
	}
	
	public class State {
		JythonInterpreter jython;
		String varName;
		
		private State(JythonInterpreter jython) {
			this.jython = jython;
			varName = "state";
			jython.exec("%s = [None for i in range(len(mln.gndAtoms))]", varName);
		}

		public void set(String gndAtom, boolean value) throws ConversionException {
			int idxGA = jython.eval("mln.gndAtoms['%s'].idx", gndAtom).asInt(0);
			jython.exec("%s[%d] = %s", varName, idxGA, value ? "True" : "False");
		}
	}
	
	public class GroundFormula {
		public int idxGF, idxF;
		public JythonInterpreter jython;
		
		public GroundFormula(JythonInterpreter jython, int idxGF) throws ConversionException {
			this.jython = jython;
			this.idxGF = idxGF;		
			this.idxF = jython.eval("mln.gndFormulas[%d].idxFormula", idxGF).asInt(0);
		}
		
		public Vector<String> getGroundAtoms() {
			PyObject list = jython.eval("map(lambda x: mln.gndAtomsByIdx[x], mln.gndFormulas[%d].idxGroundAtoms())",  idxGF);
			Vector<String> v = new Vector<String>();
			for(int i = 0; i < list.__len__(); i++) {
				v.add(list.__getitem__(i).__str__().toString());
			}
			return v;
		}
		
		public boolean isTrue(State state) throws ConversionException {
			return jython.eval("mln.gndFormulas[%d].isTrue(%s)", idxGF, state.varName).asInt(0) != 0;
		}
	}
	
	protected class GroundFormulaIteration implements Iterator<GroundFormula>, Iterable<GroundFormula> {

		protected BayesianLogicNetwork bln;
		protected int i, count;
		
		public GroundFormulaIteration(BayesianLogicNetwork bln) throws ConversionException {
			this.bln = bln;
			i = 0;
			count = bln.jython.eval("len(mln.gndFormulas)").asInt(0);
		}
		
		public boolean hasNext() {
			return i < count;
		}

		public GroundFormula next() {
			try {
				return new GroundFormula(bln.jython, i++);
			}
			catch (ConversionException e) {
				throw new RuntimeException(e.getMessage());
			}			
		}

		public void remove() {
			throw new RuntimeException("Remove not supported by this iterator.");
		}

		public Iterator<GroundFormula> iterator() {			
			return this;
		}		
	}
}
