package edu.tum.cs.bayesnets.learning.relational;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.bayesnets.core.relational.BLOGModel;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.core.relational.RelationalNode;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork.Signature;

public class Database {

	protected HashMap<String, Variable> entries;
	protected HashMap<String, HashSet<String>> domains;
	protected RelationalBeliefNetwork bn;
	
	public Database(RelationalBeliefNetwork bn) {
		this.bn = bn;
		entries = new HashMap<String, Variable>();
		domains = new HashMap<String, HashSet<String>>();
	}
	
	/**
	 * gets a variable's value as stored in the database - or, if the closed world assumption is being made, the default value of false if applicable
	 * @param varName
	 * @param closedWorld
	 * @return
	 */
	public String getVariableValue(String varName, boolean closedWorld) {
		Variable var = this.entries.get(varName);
		if(var != null)
			return var.value;
		if(closedWorld) {
			String nodeName = varName.substring(0, varName.indexOf('('));
			Signature sig = bn.getSignature(nodeName);
			if(sig.returnType.equals("Boolean"))
				return "false";
		}
		return null;
	}
	
	public void readBLOGDB(String databaseFilename) throws Exception {
		// read file content
		String dbContent = BLOGModel.readTextFile(databaseFilename);
		
		// remove comments
		Pattern comments = Pattern.compile("//.*$|/\\*.*\\*/", Pattern.MULTILINE);
		Matcher matcher = comments.matcher(dbContent);
		dbContent = matcher.replaceAll("");		

		// read entries
		Pattern entry = Pattern.compile("(\\w+)\\((.*)\\)\\s*=\\s*([^;]*);?");
		BufferedReader br = new BufferedReader(new StringReader(dbContent));
		String line;
		while((line = br.readLine()) != null) {
			line = line.trim();
			matcher = entry.matcher(line);
			if(matcher.matches()) {
				String key = matcher.group(1) + "(" + matcher.group(2).replaceAll("\\s*", "") + ")";
				Variable var = new Variable(matcher.group(1), matcher.group(2).split("\\s*,\\s*"), matcher.group(3));
				System.out.println(var.toString());
				entries.put(key, var);
				//System.out.println("key '" +  key +"'");
			}
		}
		
		// fill domains
		domains = new HashMap<String, HashSet<String>>();
		for(Variable var : entries.values()) {
			Signature sig = bn.getSignature(var.nodeName);
			if(sig == null)
				throw new Exception(String.format("Error: type %s not declared in BLOG model.", var.nodeName));
			fillDomain(sig.returnType, var.value);
			for(int i = 0; i < sig.argTypes.length; i++)
				fillDomain(sig.argTypes[i], var.params[i]);
		}
	}	

	protected void fillDomain(String type, String value) {
		HashSet<String> dom = domains.get(type);
		if(dom == null) {
			dom = new HashSet<String>();
			domains.put(type, dom);
		}
		if(!dom.contains(value))
			dom.add(value);		
	}

	public Set<String> getDomain(String domName) {
		return domains.get(domName);
	}
	
	public Collection<Variable> getEntries() {
		return entries.values();
	}
	
	public class Variable {
		public String nodeName;
		public String[] params;
		public String value;
		
		public Variable(String predicate, String[] params, String value) {
			this.nodeName = predicate;
			this.params = params;
			this.value = value;
		}
		
		public String toString() {
			return nodeName + "(" + RelationalNode.join(",", params) + ") = " + value;			
		}
	}
}
