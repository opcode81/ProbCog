/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain.
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
package probcog.bayesnets.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.util.FileUtil;
import probcog.exception.ProbCogException;

/**
 * represents an evidence database for Bayesian networks
 * 
 * a database file contains assignments of the form
 * VarName = SomeValue
 * 
 * - one assignment per line (left: variable name, right: value)
 * - whitespace is ignored
 * - Java/C++-style comments may appear anywhere in the file
 * 
 * @author Dominik Jain
 */
public class BNDatabase {
	protected HashMap<String, String> entries = new HashMap<String,String>();
	
	/**
	 * constructs an empty database
	 */
	public BNDatabase() { }
	
	/**
	 * constructs a database with the data from the given .bndb file
	 * @param f
	 * @throws ProbCogException 
	 */
	public BNDatabase(File f) throws ProbCogException {
		this();
		read(f);
	}
	
	public BNDatabase(BeliefNetworkEx bn, int[] evidenceDomainIndices) throws ProbCogException {
		BeliefNode[] nodes = bn.getNodes();
		if(evidenceDomainIndices.length != nodes.length)
			throw new ProbCogException("Evidence vector length does not match belief network");
		for(int i = 0; i < evidenceDomainIndices.length; i++) {
			if(evidenceDomainIndices[i] != -1) {
				this.add(nodes[i].getName(), nodes[i].getDomain().getName(evidenceDomainIndices[i]));
			}
		}
	}
	
	/**
	 * reads a .bndb file
	 */
	public void read(File f) throws ProbCogException {
		try {
			// read file content
			String dbContent = FileUtil.readTextFile(f);				
			// remove comments
			Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
			Matcher matcher = comments.matcher(dbContent);
			dbContent = matcher.replaceAll("");
			// read entries
			BufferedReader br = new BufferedReader(new StringReader(dbContent));
			String line;
			while((line = br.readLine()) != null) {
				line = line.trim();			
				if(line.length() > 0) {
					String[] entry = line.split("\\s*=\\s*");
					if(entry.length != 2)
						throw new ProbCogException("Incorrectly formatted evidence entry: " + line);
					add(entry[0], entry[1]);
				}
			}
		} catch (IOException e) {
			throw new ProbCogException(e);
		}
	}
	
	public Set<Entry<String,String>> getEntries() {
		return entries.entrySet();
	}
	
	public void add(String varName, String value) {
		entries.put(varName, value);
	}
	
	public void write(PrintStream out) {
		for(Entry<String,String> e : getEntries()) {
			out.printf("%s = %s\n", e.getKey(), e.getValue());
		}
	}
	
	public int size() {
		return entries.size();
	}
}
