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
import edu.tum.cs.srl.bayesnets.ABLModel;
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
public abstract class GenericDatabase<VariableType extends AbstractVariable<?>, VarValueType> implements IParameterHandler {

	/**
	 * maps variable names to Variable objects containing values
	 */
	protected HashMap<String, VariableType> entries;
	protected HashMap<RelationKey, HashMap<String, String[]>> functionalDependencies;
	protected HashMap<String, HashSet<String>> domains;
	public RelationalModel model;
	protected PrologKnowledgeBase prolog;
	/**
	 * whether any Prolog value that is computed is always cached by saving it to the database.
	 * The default value is false, because especially during learning, caching all Prolog values
	 * may require excessive amounts of memory.
	 */
	protected boolean cachePrologValues = false;
	/**
	 * true iff the database was extended with all the values that can be computed with the Prolog KB, i.e. 
	 * it is true iff all corresponding variables have been explicitly added to the database
	 */
	protected Boolean prologDatabaseExtended = false;
	protected boolean immutable = false;

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
	public GenericDatabase(RelationalModel model) throws Exception {
		this.model = model;
		entries = new HashMap<String, VariableType>();
		domains = new HashMap<String, HashSet<String>>();
		functionalDependencies = new HashMap<RelationKey, HashMap<String, String[]>>();
		taxonomy = model.getTaxonomy();
		paramHandler = new ParameterHandler(this);
		paramHandler.add("debug", "setDebug");
		paramHandler.add("debug", "setVerbose");
		
		// initialize domains
		if(taxonomy != null) {
			entity2type = new HashMap<String, String>();
			multiDomains = new HashMap<String, MultiIterator<String>>();
			for(Concept c : model.getTaxonomy().getConcepts()) {
				domains.put(c.name, new HashSet<String>());
			}
		}
		
		// fill domains with guaranteed domain elements		
		for(Entry<String, ? extends Collection<String>> e : model.getGuaranteedDomainElements().entrySet()) {
			for(String element : e.getValue())
				fillDomain(e.getKey(), element);
		}

		Collection<String> prologRules = model.getPrologRules();
		prolog = new PrologKnowledgeBase();
		if(prologRules != null && !prologRules.isEmpty()) {
			System.out.println("building Prolog knowledge base... ");
			for(String rule : prologRules) { 
				try {
					System.out.println("telling " + rule);
					prolog.tell(rule);
				}
				catch(Throwable e) {
					System.out.println("DID catch");
					throw new Exception("Error processing rule '" + rule + "'", e);
				}
			}
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
	public abstract VarValueType getVariableValue(String varName, boolean closedWorld) throws Exception;
	
	public abstract String getSingleVariableValue(String varName, boolean closedWorld) throws Exception;

	/**
	 * retrieves a variable setting
	 * 
	 * @param varName
	 *            the name of the variable to retrieve
	 * @return returns the variable setting with the given name if it is
	 *         contained in the database), null otherwise
	 * @deprecated because it does not really work with prolog predicates
	 */	
	public VariableType getVariable(String varName) {
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
	public boolean addVariable(VariableType var) throws Exception {
		return addVariable(var, false, true);
	}

	protected boolean addVariable(VariableType var, boolean ignoreUndefinedFunctions, boolean doPrologAssertions) throws Exception {
		if(immutable)
			throw new Exception("Tried to add a value to an immutable database");
		
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
			fillDomain(sig.returnType, var);
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
	
	public abstract void fillDomain(String domName, VariableType var) throws Exception;

	
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
		Pattern re_domDecl = Pattern.compile("(\\w+)\\s*=\\s*\\{(.*?)\\}");
		BufferedReader br = new BufferedReader(new StringReader(dbContent));
		String line;
		int numVars = 0;
		while((line = br.readLine()) != null) {
			line = line.trim();
			// parse domain decls
			matcher = re_domDecl.matcher(line);
			if(matcher.matches()) { // parse domain decls
				String domName = matcher.group(1);
				String[] constants = matcher.group(2).split("\\s*,\\s*");
				constants = ABLModel.makeDomainElements(constants);
				for(String c : constants)
					fillDomain(domName, c);
				continue;
			}
			// parse variable assignment
			VariableType var = readEntry(line);
			if(var != null) {
				addVariable(var, ignoreUndefinedNodes, true);
				if(++numVars % 100 == 0 && verbose)
					System.out.print("    " + numVars + " vars read\r");
				continue;
			}
			// something else
			if(line.length() != 0) {
				throw new Exception("Database entry could not be read: " + line);
			}
		}
	}
	
	protected abstract VariableType readEntry(String line) throws Exception;


	/**
	 * adds to the domain type the given value
	 * 
	 * @param type
	 *            name of the domain/type
	 * @param value
	 *            the value/entity name to add
	 * @throws Exception
	 */
	public void fillDomain(String type, String value) throws Exception {
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
	 * retrieves all entries in the database 
	 * @return
	 * @throws Exception
	 */
	public Collection<VariableType> getEntries() throws Exception {
		finalize();
		return entries.values();
	}
	
	/**
	 * If we are using a Prolog KB, extends the database (unless it has already been extended)
	 * @throws Exception
	 */
	protected void extendWithPrologValues() throws Exception {
		// TODO This does quite a bit of perhaps unnecessary work; it might be better to let Prolog compute just the instances that hold in a single query
		if(debug) System.out.println("extending database with Prolog values...");
		if(prolog != null && !prologDatabaseExtended) {			
			prologDatabaseExtended = true;
			for(Signature sig : this.model.getSignatures()) {
				if(sig.isLogical) {
					Collection<String[]> bindings = ParameterGrounder.generateGroundings(sig, this);
					for(String[] b : bindings) 
						getPrologValue(sig, b, true);
				}
			}			
		}		
	}
	
	/**
	 * makes sure this database is finalized, i.e. all values that can be derived via prolog,
	 * have been computed and renders the database immutable.
	 * There is no harm in calling this function several times.
	 */
	public void finalize() throws Exception {
		extendWithPrologValues();		
		immutable = true;
	}
	
	public boolean isFinalized() {
		return immutable;
	}
	
	/**
	 * computes the value of a variable via Prolog and adds it to the database
	 * @param sig
	 * @param args
	 * @return
	 * @throws Exception 
	 */
	protected boolean getPrologValue(Signature sig, String[] args, boolean forceAddToDatabase) throws Exception {
		String[] prologArgs = new String[args.length];
		for(int j = 0; j < args.length; j++)
			prologArgs[j] = args[j].substring(0, 1).toLowerCase() + args[j].substring(1);
		boolean value = prolog.ask(Signature.formatVarName(sig.functionName, prologArgs));
		VariableType var = makeVar(sig.functionName, args, value ? "True" : "False");
		if(cachePrologValues || forceAddToDatabase) {
			boolean added = addVariable(var, false, false);
			if(added && debug) 
				System.out.println("Prolog: computed " + var);
		}
		return value;
	}
	
	protected abstract VariableType makeVar(String functionName, String[] args, String value);

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
				VariableType var = makeVar(sig.functionName, params.clone(), "False");
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
		for(VariableType v : getEntries())
			System.out.println(v.toString());
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

	@Override
	public ParameterHandler getParameterHandler() {		
		return paramHandler;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
}
