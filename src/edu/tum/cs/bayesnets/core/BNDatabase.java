/*
 * Created on Jan 19, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.util.FileUtil;

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
 * @author jain
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
	 * @throws Exception 
	 */
	public BNDatabase(File f) throws Exception {
		this();
		read(f);
	}
	
	public BNDatabase(BeliefNetworkEx bn, int[] evidenceDomainIndices) throws Exception {
		BeliefNode[] nodes = bn.getNodes();
		if(evidenceDomainIndices.length != nodes.length)
			throw new Exception("evidence vector length does not match belief network");
		for(int i = 0; i < evidenceDomainIndices.length; i++) {
			if(evidenceDomainIndices[i] != -1) {
				this.add(nodes[i].getName(), nodes[i].getDomain().getName(evidenceDomainIndices[i]));
			}
		}
	}
	
	/**
	 * reads a .bndb file
	 */
	public void read(File f) throws Exception {
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
					throw new Exception("Incorrectly formatted evidence entry: " + line);
				add(entry[0], entry[1]);
			}
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
