package edu.tum.cs.srl.bayesnets.bln.py;

import java.util.Iterator;

import org.python.core.PyObject.ConversionException;


public class GroundFormulaIteration implements Iterator<GroundFormula>, Iterable<GroundFormula> {

	protected BayesianLogicNetworkPy bln;
	protected int i, count;
	
	public GroundFormulaIteration(BayesianLogicNetworkPy bln) throws ConversionException {
		this.bln = bln;
		i = 0;
		count = bln.jython.evalInt("len(mln.gndFormulas)");
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
		throw new RuntimeException("Remove is not supported by this iterator.");
	}

	public Iterator<GroundFormula> iterator() {			
		return this;
	}		
	
	/**
	 * gets the number of objects in this iteration
	 * @return
	 */
	public int getCount() {
		return count;
	}
}
