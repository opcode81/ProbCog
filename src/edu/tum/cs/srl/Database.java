package edu.tum.cs.srl;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.prolog.PrologKnowledgeBase;
import edu.tum.cs.srl.taxonomy.Concept;
import edu.tum.cs.srl.taxonomy.Taxonomy;
import edu.tum.cs.util.FileUtil;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.MultiIterator;

/**
 * represents an evidence/training database for a relational model
 * 
 * @author jain
 */
public class Database implements IParameterHandler {

	/**
	 * maps variable names to Variable objects containing values
	 */
	protected HashMap<String, Variable> entries;
	protected HashMap<RelationKey, HashMap<String, String[]>> functionalDependencies;
	protected HashMap<String, HashSet<String>> domains;
	public RelationalModel model;
	protected PrologKnowledgeBase prolog;
	protected Boolean prologDatabaseExtended = false;

	// taxonomy-related variables

	protected Taxonomy taxonomy;
	protected HashMap<String, String> entity2type;
	protected HashMap<String, MultiIterator<String>> multiDomains;

	protected boolean debug = false;
	protected boolean verbose = false;
	protected ParameterHandler paramHandler;

	/**
	 * constructs an empty database for the given model
	 * 
	 * @param model
	 * @throws Exception
	 */
	public Database(RelationalModel model) throws Exception {
		this.model = model;
		entries = new HashMap<String, Variable>();
		domains = new HashMap<String, HashSet<String>>();
		functionalDependencies = new HashMap<RelationKey, HashMap<String, String[]>>();
		paramHandler = new ParameterHandler(this);
		paramHandler.add("debug", "setDebug");
		paramHandler.add("debug", "setVerbose");

		// fill domains with guaranteed domain elements
		for(Entry<String, String[]> e : model.getGuaranteedDomainElements().entrySet()) {
			for(String element : e.getValue())
				fillDomain(e.getKey(), element);
		}

		Collection<String> prologRules = model.getPrologRules();
		if(prologRules != null && !prologRules.isEmpty()) {
			System.out.println("  building prolog knowledge base... ");
			prolog = new PrologKnowledgeBase(); // TODO: If no rules in ABL
												// (just logical definition)
												// prologRules are empty and no
												// Knowledge Base is
												// initialized!
			for(String rule : prologRules) {
				prolog.tell(rule);
			}
		}

		// taxonomy-related stuff
		taxonomy = model.getTaxonomy();
		if(taxonomy != null) {
			entity2type = new HashMap<String, String>();
			multiDomains = new HashMap<String, MultiIterator<String>>();
		}
	}
	
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * gets a variable's value as stored in the database
	 * 
	 * @param varName
	 *            the name of the variable whose value is to be retrieved
	 * @param closedWorld
	 *            whether to make the closed-world assumption, i.e. to assume
	 *            that any Boolean variable for which we do not have a value is
	 *            "False"
	 * @return If a value for the given variable is stored in the database (or
	 *         can be computed based on Prolog rules), it is returned. 
	 *         Otherwise, null is returned - unless the closed world
	 *         assumption is being made and the variable is boolean, in which
	 *         case the default value of "False" is returned.
	 * @throws Exception
	 */
	public String getVariableValue(String varName, boolean closedWorld) throws Exception {
		String lowerCaseName = varName.toLowerCase();
		Variable var = this.entries.get(lowerCaseName);
		// if we have the value, return it
		if(var != null)
			return var.value;
		
		// otherwise, get the signature
		int braceIndex = varName.indexOf('(');
		String nodeName = varName.substring(0, braceIndex);
		Signature sig = model.getSignature(nodeName);		

		// if it's a logically determined predicate, use prolog to retrieve a value
		if(sig.isLogical) {
			String[] args = varName.substring(braceIndex+1, varName.length()-1).split("\\s*,\\s*");
			return getPrologValue(sig, args) ? "True" : "False"; 
		}
		
		// if we are making the closed assumption return the default value of
		// false for boolean predicates or raise an exception for non-boolean
		// functions
		if(closedWorld) {
			if(sig.isBoolean())
				return "False";
			else {
				throw new Exception("Missing database value of " + varName + " - cannot apply closed-world assumption because domain is not boolean: " + sig.returnType);
			}
		}
		
		return null;
	}

	/**
	 * retrieves a variable setting
	 * 
	 * @param varName
	 *            the name of the variable to retrieve
	 * @return returns the variable setting with the given name if it is
	 *         contained in the database), null otherwise
	 * @deprecated because it does not really work with prolog predicates
	 */
	public Variable getVariable(String varName) {
		return entries.get(varName.toLowerCase());
	}

	/**
	 * checks whether the database contains an entry for the given variable name
	 */
	public boolean contains(String varName) {
		if(entries.containsKey(varName.toLowerCase()))
			return true;
		
		//Matcher m = Pattern.compile("(\\w+)\\((\\.*?)\\)").matcher(varName);
		// for logically determined functions, we always have a value
		String functionName = varName.substring(0, varName.indexOf('(')); //m.group(1);
		Signature sig = model.getSignature(functionName);
		return sig.isLogical;
	}

	/**
	 * adds the given variable to the database if it isn't already present
	 */
	public boolean addVariable(Variable var) throws Exception {
		return addVariable(var, false, true);
	}

	protected boolean addVariable(Variable var, boolean ignoreUndefinedFunctions, boolean doPrologAssertions) throws Exception {
		boolean ret = false;
		String entryKey = var.getKeyString().toLowerCase();
		if(entries.containsKey(entryKey))
			return ret;
		
		// if(debug) System.out.println("adding var " + var);
		
		// fill domains
		Signature sig = model.getSignature(var.functionName);
		if(sig == null) {
			// if the predicate is not in the model, end here
			if(ignoreUndefinedFunctions)
				return ret;
			else
				throw new Exception(String.format("Function %s appears in the data but is not declared in the model.", var.functionName));
		}

		if(sig.isLogical && doPrologAssertions) { // for logically determined functions, assert any true instances to the Prolog KB
			if(var.isTrue()) {
				String func = var.functionName;
				func = func.substring(0, 1).toLowerCase() + func.substring(1);
				String line = func + "(";
				for(String par : var.params) {
					line += par.substring(0, 1).toLowerCase() + par.substring(1) + ",";
				}
				line = line.substring(0, line.length() - 1) + ")";
				if(debug) System.out.println("Prolog: asserted " + line);
				prolog.tell(line + ".");
			}
		}

		if(sig.argTypes.length != var.params.length)
			throw new Exception("The database entry '" + var.getKeyString() + "' is not compatible with the signature definition of the corresponding function: expected " + sig.argTypes.length + " parameters as per the signature, got " + var.params.length + ".");
		// if(domains.get(sig.returnType) == null || !domains.get(sig.returnType).contains(var.value))
		// System.out.println("adding " + var.value + " to " + sig.returnType + " because of " + var);
		if(!sig.isBoolean())
			fillDomain(sig.returnType, var.value);
		for(int i = 0; i < sig.argTypes.length; i++) {
			// if(domains.get(sig.argTypes[i]) == null || !domains.get(sig.argTypes[i]).contains(var.params[i]))
			//   System.out.println("adding " + var.params[i] + " to " + sig.argTypes[i] + " because of " + var);
			fillDomain(sig.argTypes[i], var.params[i]);
		}

		// add the entry to the main store
		entries.put(entryKey, var);
		ret = true;

		// update lookup tables for keys
		// (but only if value is true)
		Collection<RelationKey> keys = this.model.getRelationKeys(var.functionName);
		if(keys != null) {
			// add lookup entry if the variable value is true
			if(!var.isTrue())
				return ret;
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
		return ret;
	}

	public String[] getParameterSet(RelationKey key, String[] keyValues) {
		// System.out.println("doing lookup for " + this.key + " with " +
		// StringTool.join(", ", keyValues));
		HashMap<String, String[]> m = functionalDependencies.get(key);
		if(m == null)
			return null;
		return m.get(StringTool.join(",", keyValues));
	}

	public void readBLOGDB(String databaseFilename) throws Exception {
		readBLOGDB(databaseFilename, false);
	}

	public void readBLOGDB(String databaseFilename, boolean ignoreUndefinedNodes) throws Exception {
		// read file content
		if(verbose)
			System.out.printf("  reading contents of %s...\n", databaseFilename);
		String dbContent = FileUtil.readTextFile(databaseFilename);

		// remove comments
		if(verbose)
			System.out.println("  removing comments");
		Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = comments.matcher(dbContent);
		dbContent = matcher.replaceAll("");

		// read lines
		if(verbose)
			System.out.println("  reading items");
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
				// String key = matcher.group(1) + "(" +
				// matcher.group(2).replaceAll("\\s*", "") + ")";
				Variable var = new Variable(matcher.group(1), matcher.group(2).split("\\s*,\\s*"), matcher.group(3), model);
				// System.out.println(var.toString());
				addVariable(var, ignoreUndefinedNodes, true);
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
				throw new Exception("Database entry could not be read: " + line);
			}
		}
	}

	/**
	 * adds to the domain type the given value
	 * 
	 * @param type
	 *            name of the domain/type
	 * @param value
	 *            the value/entity name to add
	 * @throws Exception
	 */
	protected void fillDomain(String type, String value) throws Exception {
		// if(debug) System.out.printf("  adding %s to domain %s\n", value, type);
		// if we are working with a taxonomy, we need to check whether we
		// previously assigned the value to a super-type of type
		// and if so, reassign it to the sub-type
		if(taxonomy != null) {
			String prevType = entity2type.get(value);
			if(prevType != null) {
				if(prevType.equals(type))
					return;
				// new type is sub-type --> reassign 
				if(taxonomy.query_isa(type, prevType)) 
					domains.get(prevType).remove(value);
				// new type is supertype --> do nothing (old assignment was more specific)
				else if(taxonomy.query_isa(prevType, type)) 
					return;
				else
					;// System.err.printf("Warning: Entity " + value + " belongs to at least two types (%s, %s) which have no taxonomic relationship; functional mapping of entities to types not well-defined if domains are not merged.");
			}
			entity2type.put(value, type);
		}
		// add to domain if not already present
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
			for(int j = i + 1; j < doms.size(); j++) {
				// compare the i-th domain to the j-th
				HashSet<String> dom1 = doms.get(i);
				HashSet<String> dom2 = doms.get(j);
				for(String value : dom1) {
					if(dom2.contains(value)) { // replace all occurrences of
						// the j-th domain by the i-th
						if(verbose)
							System.out.println("Domains " + domNames.get(i) + " and " + domNames.get(j) + " overlap (both contain " + value + "). Merging...");
						String targetDomName = domNames.get(i);
						this.model.replaceType(domNames.get(j), targetDomName);
						// add all elements of j-th domain to the i-th
						dom1.addAll(dom2);
						doms.set(j, dom1);
						for(String v : dom2)
							entity2type.put(v, targetDomName);
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
	 * @throws Exception
	 */
	public Iterable<String> getDomain(String domName) throws Exception {
		if(taxonomy == null)
			return domains.get(domName);
		else { // if we have a taxonomy, the domain is the combination of domains of the given type and all of its sub-types
			MultiIterator<String> dom = multiDomains.get(domName);
			if(dom != null)
				return dom;
			dom = new MultiIterator<String>();
			boolean isEmpty = true;
			for(Concept c : taxonomy.getDescendants(domName)) {
				Iterable<String> subdom = domains.get(c.name);
				if(subdom != null) {
					dom.add(subdom);
					isEmpty = false;
				}
			}
			if(isEmpty)
				dom = null;
			multiDomains.put(domName, dom);
			return dom;
		}
	}

	/**
	 * retrieves all entries in the database TODO Because of the prolog database
	 * extension, calling this method should render the database immutable
	 * 
	 * @return
	 * @throws Exception
	 */
	public Collection<Variable> getEntries() throws Exception {
		// If we are using a Prolog KB, extend the database (unless it has already been extended)
		// TODO This does quite a bit of unnecessary work; it might be better to let Prolog compute just the instances that hold in a single query 
		if(prolog != null && !prologDatabaseExtended) {			
			prologDatabaseExtended = true;
			for(Signature sig : this.model.getSignatures()) {
				if(sig.isLogical) {
					Collection<String[]> bindings = ParameterGrounder.generateGroundings(sig, this);
					for(String[] b : bindings) 
						getPrologValue(sig, b);
				}
			}			
		}
		return entries.values();
	}
	
	/**
	 * computes the value of a variable via Prolog and adds it to the database
	 * @param sig
	 * @param args
	 * @return
	 * @throws Exception 
	 */
	protected boolean getPrologValue(Signature sig, String[] args) throws Exception {
		String[] prologArgs = new String[args.length];
		for(int j = 0; j < args.length; j++)
			prologArgs[j] = args[j].substring(0, 1).toLowerCase() + args[j].substring(1);
		boolean value = prolog.ask(Signature.formatVarName(sig.functionName, prologArgs));
		Variable var = new Variable(sig.functionName, args, value ? "True" : "False", model);
		boolean added = addVariable(var, false, false);
		if(added && debug) 
			System.out.println("Prolog: computed " + var);
		return value;
	}

	/**
	 * @return the values of this database as an array of String[2] arrays,
	 *         where the first element of each is the name of the variable, and
	 *         the second is the value
	 * @throws Exception
	 */
	public String[][] getEntriesAsArray() throws Exception {
		Collection<Variable> vars = getEntries();
		String[][] ret = new String[entries.size()][2];
		int i = 0;
		for(Variable var : vars) {
			ret[i][0] = var.getKeyString();
			ret[i][1] = var.value;
			i++;
		}
		return ret;
	}

	/**
	 * adds all missing values of ground atoms of the given predicate, setting
	 * them to "False". Invoke <i>after</i> the database has been read!
	 * 
	 * @param predName
	 * @throws Exception
	 */
	public void setClosedWorldPred(String predName) throws Exception {
		Signature sig = this.model.getSignature(predName);
		if(sig == null)
			throw new Exception("Cannot determine signature of " + predName);
		String[] params = new String[sig.argTypes.length];
		setClosedWorldPred(sig, 0, params);
	}

	protected void setClosedWorldPred(Signature sig, int i, String[] params) throws Exception {
		if(i == params.length) {
			String varName = Signature.formatVarName(sig.functionName, params);
			if(!this.contains(varName)) {
				Variable var = new Variable(sig.functionName, params.clone(), "False", model);
				this.addVariable(var);
			}
			return;
		}
		Iterable<String> dom = this.getDomain(sig.argTypes[i]);
		if(dom == null)
			return;
		for(String value : dom) {
			params[i] = value;
			setClosedWorldPred(sig, i + 1, params);
		}
	}

	public Signature getSignature(String functionName) {
		return model.getSignature(functionName);
	}

	public void printDomain(PrintStream out) {
		for(Entry<String, HashSet<String>> e : domains.entrySet()) {
			out.println(e.getKey() + ": " + StringTool.join(", ", e.getValue()));
		}
	}

	public void print() throws Exception {
		for(Variable v : getEntries())
			System.out.println(v.toString());
	}

	/**
	 * 
	 * @param databaseFilename
	 * @throws java.lang.Exception
	 */
	public void readMLNDB(String databaseFilename) throws Exception {
		readMLNDB(databaseFilename, false);
	}

	/**
     * 
     * */
	public void readMLNDB(String databaseFilename, boolean ignoreUndefinedNodes) throws Exception {
		boolean verbose = false;

		// read file content
		if(verbose)
			System.out.printf("reading contents of %s...\n", databaseFilename);
		String dbContent = FileUtil.readTextFile(databaseFilename);

		// remove comments
		// if (verbose) System.out.println("  removing comments...");
		Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = comments.matcher(dbContent);
		dbContent = matcher.replaceAll("");

		// read lines
		// if (verbose) System.out.println("  reading items...");
		String arg = "\\w+";
		String argList = "\\s*" + arg + "\\s*(?:,\\s*" + arg + "\\s*)*";
		Pattern re_entry = Pattern.compile("(!?\\w+)\\((" + argList + ")\\)");
		Pattern re_domDecl = Pattern.compile("(\\w+)\\s*=\\s*\\{(" + argList + ")\\}");
		BufferedReader br = new BufferedReader(new StringReader(dbContent));
		String line;
		Variable var;
		while((line = br.readLine()) != null) {
			line = line.trim();
			// parse variable assignment
			matcher = re_entry.matcher(line);
			if(matcher.matches()) {
				if(matcher.group(1).startsWith("!"))
					var = new Variable(matcher.group(1).substring(1), matcher.group(2).trim().split("\\s*,\\s*"), "False", model);
				else
					var = new Variable(matcher.group(1), matcher.group(2).trim().split("\\s*,\\s*"), "True", model);

				addVariable(var, ignoreUndefinedNodes, true);
				// if (++numVars % 100 == 0 && verbose)
				// System.out.println("    " + numVars + " vars read\r");
				continue;
			}

			// parse domain extension
			matcher = re_domDecl.matcher(line);
			if(matcher.matches()) { // parse
				String domNam = matcher.group(1);
				String[] constants = matcher.group(2).trim().split("\\s*,\\s*");
				for(String c : constants)
					fillDomain(domNam, c);
				continue;
			}
			// something else
			if(line.length() != 0)
				System.err.println("Line could not be read: " + line);
		}
	}

	/**
	 * 
	 * @return
	 */
	public HashMap<String, HashSet<String>> getDomains() throws Exception {
		if(taxonomy != null)
			throw new Exception("Cannot safely return the set of domains for a model that uses a taxonomy");
		return domains;
	}

	public RelationalModel getModel() {
		return this.model;
	}

	/**
	 * gets the type of the given constant by searching through the domains
	 * 
	 * @param constant
	 * @return the type name or null if the constant is unknown
	 */
	public String getConstantType(String constant) {
		for(Entry<String, HashSet<String>> e : this.domains.entrySet()) {
			if(e.getValue().contains(constant)) {
				return e.getKey();
			}
		}
		return null;
	}

	public static class Variable extends edu.tum.cs.srl.AbstractVariable {

		RelationalModel model;

		public Variable(String functionName, String[] params, String value, RelationalModel model) {
			super(functionName, params, value);
			this.model = model;
		}

		public String getPredicate() {
			if(isBoolean())
				return functionName + "(" + StringTool.join(",", params) + ")";
			else
				return functionName + "(" + StringTool.join(",", params) + "," + value + ")";
		}

		public boolean isBoolean() {
			return model.getSignature(functionName).isBoolean();
		}

		@Override
		public String toString() {
			return String.format("%s = %s", Signature.formatVarName(functionName, params), value);
		}
	}

	@Override
	public ParameterHandler getParameterHandler() {		
		return paramHandler;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
}
