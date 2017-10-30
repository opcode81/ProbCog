/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
package probcog.inference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

import probcog.exception.ProbCogException;

/**
 * Generally usable representation of a distribution that can be written to a file.
 * Any BasicSampledDistribution can be converted to this type of object.
 * @author Dominik Jain
 */
public class GeneralSampledDistribution extends BasicSampledDistribution {

	protected String[] varNames;
	protected String[][] domains;
	protected HashMap<String,Integer> varName2Index;	
	
	public GeneralSampledDistribution(double[][] values, Double Z, String[] varNames, String[][] domains) throws ProbCogException {
		this.values = values;
		this.Z = Z;
		this.varNames = varNames;
		this.domains = domains;
		varName2Index = new HashMap<String,Integer>();
		for(int i = 0; i < varNames.length; i++)
			varName2Index.put(varNames[i], i);
	}
	
	@Override
	public String[] getDomain(int idx) {
		return domains[idx];
	}

	@Override
	public String getVariableName(int idx) {
		return varNames[idx];
	}

	/**
	 * writes this object to a file
	 * @param f
	 * @throws ProbCogException 
	 */
	public void write(File f) throws ProbCogException {
		try {
			FileOutputStream fos = new FileOutputStream(f);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this.values);
			oos.writeObject(this.Z);
			oos.writeObject(varNames);
			oos.writeObject(domains);
			oos.close();
		}
		catch (IOException e) {
			throw new ProbCogException(e);
		}
	}
	
	/**
	 * reads a previously stored distribution from a file
	 * @param s
	 * @return
	 * @throws ProbCogException 
	 */
	public static GeneralSampledDistribution fromFile(File f) throws ProbCogException {
		try {
			java.io.ObjectInputStream objstream = new ObjectInputStream(new FileInputStream(f));
		    double[][] values = (double[][])objstream.readObject();
		    Double Z = (Double)objstream.readObject();
		    String[] varNames = (String[])objstream.readObject();
		    String[][] domains = (String[][])objstream.readObject();
		    objstream.close();
		    return new GeneralSampledDistribution(values, Z, varNames, domains);
		}
		catch (IOException | ClassNotFoundException e) {
			throw new ProbCogException(e);
		}
	}

	@Override
	public int getVariableIndex(String name) {		
		return varName2Index.get(name);
	}

	@Override
	public Integer getNumSamples() {
		throw new RuntimeException("A GeneralizedDistribution represents only the distribution, no additional data is available");
	}
}
