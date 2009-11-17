/*
 * Created on Nov 16, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.inference;

import java.io.PrintStream;
import java.io.Serializable;

public abstract class BasicSampledDistribution implements Serializable {
	/**
	 * an array of values representing the distribution, one for each node and each domain element:
	 * values[i][j] is the value for the j-th domain element of the i-th node in the network
	 */
	public double[][] values = null;
	/**
	 * the normalization constant that applies to each of the distribution values
	 */
	public Double Z = null;
	
	public double getProbability(int varIdx, int domainIdx) {
		return values[varIdx][domainIdx] / Z;
	}
	
	public void print(PrintStream out) {
		for(int i = 0; i < values.length; i++) {
			printVariableDistribution(out, i);
		}
	}
	
	public void printVariableDistribution(PrintStream out, int idx) {
		out.println(getVariableName(idx) + ":");
		String[] domain = getDomain(idx);
		for(int j = 0; j < domain.length; j++) {
			double prob = values[idx][j] / Z;
			out.println(String.format("  %.4f %s", prob, domain[j]));
		}
	}
	
	public abstract String getVariableName(int idx);
	public abstract int getVariableIndex(String name);
	public abstract String[] getDomain(int idx);
	
	public GeneralSampledDistribution toGeneralDistribution() {
		int numVars = values.length;
		String[] varNames = new String[numVars];
		String[][] domains = new String[numVars][];
		for(int i = 0; i < numVars; i++) {
			varNames[i] = getVariableName(i);
			domains[i] = getDomain(i);
		}
		return new GeneralSampledDistribution(this.values, this.Z, varNames, domains);
	}
	
	/**
	 * gets the mean squared error
	 * @param d
	 * @return
	 * @throws Exception
	 */
	public double getMSE(BasicSampledDistribution d) throws Exception {
		int cnt = 0;
		double sum = 0;
		for(int i = 0; i < values.length; i++) {
			int i2 = d.getVariableIndex(this.getVariableName(i));
			if(i2 < 0) 
				throw new Exception("Variable " + this.getVariableName(i) + " has no correspondence in second distribution");
			for(int j = 0; j < values[i].length; j++) {
				double error = getProbability(i, j) - d.getProbability(i2, j);
				error *= error;
				sum += error;
				cnt++;
			}
		}
		return sum / cnt;
	}
}
