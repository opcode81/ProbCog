package edu.tum.cs.bayesnets.learning.relational;

import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.core.relational.RelationalNode;

import java.io.*;
import java.util.HashMap;
import java.util.regex.*;

public class CPTLearner extends edu.tum.cs.bayesnets.learning.CPTLearner {
	
	private class Variable {
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
	
	protected HashMap<String,Variable> database;
	
	public CPTLearner(RelationalBeliefNetwork bn) throws Exception {
		super(bn);		
		database = new HashMap<String,Variable>();
	}
	
	public void learn(String databaseFilename) throws Exception {
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		File inputFile = new File(databaseFilename);
		FileReader fr = new FileReader(inputFile);
		char[] cbuf = new char[(int)inputFile.length()];
		fr.read(cbuf);
		String dbContent = new String(cbuf);
	
		// remove comments
		Pattern comments = Pattern.compile("//.*$|/\\*.*\\*/", Pattern.MULTILINE);
		Matcher matcher = comments.matcher(dbContent);
		dbContent = matcher.replaceAll("");		
		
		// build database
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
				database.put(key, var);
				//System.out.println("key '" +  key +"'");
			}
		}
		
		// process each variable
		int domainIndices[] = new int[this.nodes.length];
		for(String varName : database.keySet()) {
			Variable var = this.database.get(varName);
			RelationalNode nd = bn.getRelationalNode(var.nodeName);
			if(nd == null) {
				String error = String.format("Invalid node name '%s'", var.nodeName);
				System.err.println(error);
				continue;
				//throw new Exception(error);
			}
			ExampleCounter counter = this.counters[nd.index];
			// set the domain indices of all relevant nodes (node itself and parents)
			for(int i = 0; i < counter.nodeIndices.length; i++) {
				RelationalNode ndParent = bn.getRelationalNode(counter.nodeIndices[i]);
				// determine name of the parent node by replacing parameter bindings
				StringBuffer parentNodeName = new StringBuffer(ndParent.name + "(");
				String[] params = new String[ndParent.params.length];
				for(int parentParam = 0; parentParam < ndParent.params.length; parentParam++) {
					for(int childParam = 0; childParam < nd.params.length; childParam++) {
						if(nd.params[childParam].equals(ndParent.params[parentParam])) {
							params[parentParam] = var.params[childParam];
						}
					}
					if(params[parentParam] == null)
						throw new Exception(String.format("Could not determine parameters for node '%s' for assignment '%s'", ndParent.name, var.toString()));
					parentNodeName.append(params[parentParam]);
					if(parentParam < ndParent.params.length-1)
						parentNodeName.append(",");
				}
				parentNodeName.append(")");
				// set domain index
				Variable parentVar = database.get(parentNodeName.toString());
				if(parentVar == null)
					throw new Exception(String.format("Could not find setting for node named '%s' while processing '%s'", parentNodeName, var.toString()));
				int domain_idx = ((Discrete)(ndParent.node.getDomain())).findName(parentVar.value);
				if(domain_idx == -1)
					throw new Exception("'" + parentVar.value + "' not found in domain of " + ndParent.name);				
				domainIndices[ndParent.index] = domain_idx;
			}
			// count this example
			counter.count(domainIndices);
		}
	}
}
