package edu.tum.cs.srl;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.srl.bayesnets.BLOGModel;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.tools.StringTool;


public class Database {
	
	/**
	 * maps variable names to Variable objects containing values
	 */
	protected HashMap<String, Variable> entries;
	protected HashMap<RelationKey, HashMap<String, String[]>> functionalDependencies;
	protected HashMap<String, HashSet<String>> domains;
	public RelationalModel rbn;
	
	/**
	 * constructs an empty database for the given model
	 * @param bn
	 */
	public Database(RelationalModel bn) {
		this.rbn = bn;
		entries = new HashMap<String, Variable>();
		domains = new HashMap<String, HashSet<String>>();
		functionalDependencies = new HashMap<RelationKey, HashMap<String,String[]>>();
		// fill domains with guaranteed domain elements
		for(Entry<String, String[]> e : bn.getGuaranteedDomainElements().entrySet()) {
			for(String element : e.getValue()) 
				fillDomain(e.getKey(), element);
		}
	}
	
	/**
	 * gets a variable's value as stored in the database 
	 * @param varName  the name of the variable whose value is to be retrieved
	 * @param closedWorld  whether to make the closed-world assumption, i.e. to assume that any Boolean variable for which we do not have a value is "False"
	 * @return If a value for the given variable is stored in the database, it is returned. Otherwise, null is returned, unless the closed world assumption is being made and the variable is boolean, in which case the default value of "False" is returned.
	 * @throws Exception 
	 */
	public String getVariableValue(String varName, boolean closedWorld) throws Exception {
		Variable var = this.entries.get(varName.toLowerCase());
		// if we have the value, return it
		if(var != null)
			return var.value;
		// otherwise, return the default value of false for boolean predicates or raise an exception for non-boolean functions 
		if(closedWorld) {
			String nodeName = varName.substring(0, varName.indexOf('('));
			Signature sig = rbn.getSignature(nodeName);
			if(sig.isBoolean())
				return "False";
			else {
				throw new Exception("Missing database value of " + varName + " - cannot apply closed-world assumption because domain is not boolean: " + sig.returnType);
			}
		}
		return null;
	}

	/**
	 * checks whether the database contains an entry for the given variable name 
	 */
	public boolean contains(String varName) {
		return entries.containsKey(varName.toLowerCase());
	}
	
	public void addVariable(Variable var) throws Exception {
		addVariable(var, false);
	}
	
	public void addVariable(Variable var, boolean ignoreUndefinedFunctions) throws Exception {
		// fill domains
		Signature sig = rbn.getSignature(var.functionName);
		if(sig == null) {
			// if the predicate is not in the model, end here
			if(ignoreUndefinedFunctions)
				return;
			else
				throw new Exception(String.format("Error: node %s appears in the data but it is not declared in the model.", var.functionName));
		}
		else {
			if(sig.argTypes.length != var.params.length) 
				throw new Exception("The database entry '" + var.getKeyString() + "' is not compatible with the signature definition of the corresponding function: expected " + sig.argTypes.length + " parameters as per the signature, got " + var.params.length + ".");			
			//if(domains.get(sig.returnType) == null || !domains.get(sig.returnType).contains(var.value))
			//	System.out.println("adding " + var.value + " to " + sig.returnType + " because of " + var);
			fillDomain(sig.returnType, var.value);
			for(int i = 0; i < sig.argTypes.length; i++) {
				//if(domains.get(sig.argTypes[i]) == null || !domains.get(sig.argTypes[i]).contains(var.params[i]))
				//	System.out.println("adding " + var.params[i] + " to " + sig.argTypes[i] + " because of " + var);
				fillDomain(sig.argTypes[i], var.params[i]);
			}
		}
		
		// add the entry to the main store
		entries.put(var.getKeyString().toLowerCase(), var);
		
		// update lookup tables for keys
		// (but only if value is true)
        Collection<RelationKey> keys = this.rbn.getRelationKeys(var.functionName);
		if(keys != null) {
			// add lookup entry if the variable value is true
			if(!var.isTrue())
				return;
			// update all keys
			for(RelationKey key : keys) {
				// compute key for map entry
				StringBuffer sb = new StringBuffer();
				int i = 0; 
				for(Integer paramIdx : key.keyIndices) {
					if(i++ > 0)
						sb.append(',');
					sb.append(var.params[paramIdx]);
				}
				// add
				HashMap<String, String[]> hm = functionalDependencies.get(key);
				if(hm == null) {
					hm = new HashMap<String, String[]>(); 
					functionalDependencies.put(key, hm);
				}
				hm.put(sb.toString(), var.params);
			}
		}
	}
	

	public String[] getParameterSet(RelationKey key, String[] keyValues) {
		//System.out.println("doing lookup for " + this.key + " with " + StringTool.join(", ", keyValues));
		HashMap<String, String[]> m = functionalDependencies.get(key);
		if(m == null) return null;
		return m.get(StringTool.join(",", keyValues));
	}

	public void readBLOGDB(String databaseFilename) throws Exception {
		readBLOGDB(databaseFilename, false);
	}

	public void readBLOGDB(String databaseFilename, boolean ignoreUndefinedNodes) throws Exception {
		boolean verbose = true;
		
		// read file content
		if(verbose) System.out.println("  reading file contents");
		String dbContent = BLOGModel.readTextFile(databaseFilename);
		
		// remove comments
		if(verbose) System.out.println("  removing comments");
		Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = comments.matcher(dbContent);
		dbContent = matcher.replaceAll("");		

		// read lines
		if(verbose) System.out.println("  reading items");
		Pattern re_entry = Pattern.compile("(\\w+)\\(([^\\)]+)\\)\\s*=\\s*([^;]*);?");
		Pattern re_domDecl = Pattern.compile("(\\w+)\\s*=\\s*\\{(.*?)\\}");
		BufferedReader br = new BufferedReader(new StringReader(dbContent));
		String line;
		int numVars = 0;
		while((line = br.readLine()) != null) {
			line = line.trim();			
			// parse variable assignment
			matcher = re_entry.matcher(line);
			if(matcher.matches()) {
				//String key = matcher.group(1) + "(" + matcher.group(2).replaceAll("\\s*", "") + ")";
				Variable var = new Variable(matcher.group(1), matcher.group(2).split("\\s*,\\s*"), matcher.group(3));
				//System.out.println(var.toString());				
				addVariable(var, ignoreUndefinedNodes);
				if(++numVars % 100 == 0 && verbose)
					System.out.print("    " + numVars + " vars read\r");
				continue;
			}			
			// parse domain decls
			matcher = re_domDecl.matcher(line);
			if(matcher.matches()) { // parse domain decls
				String domName = matcher.group(1);
				String[] constants = matcher.group(2).split("\\s*,\\s*");
				for(String c : constants)
					fillDomain(domName, c);
				continue;
			}
			// something else
			if(line.length() != 0) {
				System.err.println("Line could not be read: " + line);
			}
		}
	}	

	/**
	 * adds to the domain type the given value
	 * @param type	name of the domain/type
	 * @param value	the value to add
	 */
	protected void fillDomain(String type, String value) {
		HashSet<String> dom = domains.get(type);
		if(dom == null) {
			dom = new HashSet<String>();
			domains.put(type, dom);
		}
		if(!dom.contains(value))
			dom.add(value);		
	}
	
	/**
	 * checks the domains for overlaps and merges domains if necessary
	 *
	 */
	public void checkDomains(boolean verbose) {
		ArrayList<HashSet<String>> doms = new ArrayList<HashSet<String>>();
		ArrayList<String> domNames = new ArrayList<String>();
		for(Entry<String, HashSet<String>> entry : domains.entrySet()) {
			doms.add(entry.getValue());
			domNames.add(entry.getKey());
		}
		for(int i = 0; i < doms.size(); i++) {
			for(int j = i+1; j < doms.size(); j++) {
				// compare the i-th domain to the j-th
				HashSet<String> dom1 = doms.get(i);
				HashSet<String> dom2 = doms.get(j);
				for(String value : dom1) {
					if(dom2.contains(value)) { // replace all occurrences of the j-th domain by the i-th
						if(verbose)
							System.out.println("Domains " + domNames.get(i) + " and " + domNames.get(j) + " overlap (both contain " + value + "). Merging...");
						this.rbn.replaceType(domNames.get(j), domNames.get(i));
						dom1.addAll(dom2);
						doms.set(j, dom1);
						break;
					}
				}
			}
		}
	}

	/**
	 * 
	 * @param domName
	 * @return the domain as a set of strings or null if the domain is not found
	 */
	public Set<String> getDomain(String domName) {
		return domains.get(domName);
	}
	
	/**
	 * retrieves all entries in the database
	 * @return
	 */
	public Collection<Variable> getEntries() {
		return entries.values();
	}
	
	/**
	 * @return the values of this database as an array of String[2] arrays, where the first element of each is the name of the variable, and the second is the value 
	 */
	public String[][] getEntriesAsArray() {
		String[][] ret = new String[entries.size()][2];
		int i = 0;
		for(Variable var : entries.values()) {
			ret[i][0] = var.getKeyString();
			ret[i][1] = var.value;
			i++;
		}
		return ret;
	}
	
	/**
	 * adds all missing values of ground atoms of the given predicate, setting them to "False".
	 * Invoke <i>after</i> the database has been read!
	 * @param predName
	 * @throws Exception 
	 */
	public void setClosedWorldPred(String predName) throws Exception {
		Signature sig = this.rbn.getSignature(predName);
		if(sig == null)
			throw new Exception("Cannot determine signature of " + predName);
		String[] params = new String[sig.argTypes.length];
		setClosedWorldPred(sig, 0, params);
	}
	
	protected void setClosedWorldPred(Signature sig, int i, String[] params) throws Exception {
		if(i == params.length) {
			String varName = Signature.formatVarName(sig.functionName, params);
			if(!this.contains(varName)) {				
				Variable var = new Variable(sig.functionName, params.clone(), "False");
				this.addVariable(var);
			}
			return;
		}
		Set<String> dom = this.getDomain(sig.argTypes[i]);
		if(dom == null)
			return;
		for(String value : dom) {
			params[i] = value;
			setClosedWorldPred(sig, i+1, params);
		}
	}	
	
	public static class Variable {
		/**
		 * the node name or function/predicate name
		 */
		public String functionName;
		/**
		 * the actual parameters of the function/predicate
		 */
		public String[] params;
		public String value;
		
		public Variable(String predicate, String[] params, String value) {
			this.functionName = predicate;
			this.params = params;
			this.value = value;
		}
		
		public String toString() {
			return getKeyString() + " = " + value;			
		}
		
		public String getKeyString() {
			return functionName + "(" + StringTool.join(",", params) + ")";
		}
		
		/**
		 * gets the predicate representation that corresponds to the assignment of this variable, i.e. for a(x)=v, return a(x,v) 
		 * @return
		 */
		public String getPredicate(RelationalModel rbn) {
			if(rbn.getSignature(functionName).isBoolean())
				return functionName + "(" + RelationalNode.join(",", params) + ")";
			else				
				return functionName + "(" + RelationalNode.join(",", params) + "," + value + ")";	
		}

		public boolean isTrue() {
			return value.equalsIgnoreCase("True");
		}
	}
	
	public Signature getSignature(String functionName) {
		return rbn.getSignature(functionName);
	}
	
	public void printDomain(PrintStream out) {
		for(Entry<String, HashSet<String>> e : domains.entrySet()) {
			out.println(e.getKey() + ": " + StringTool.join(", ", e.getValue()));
		}
	}
	
	public void print() {
		for(Variable v : getEntries()) 
			System.out.println(v.toString());		
	}
}
