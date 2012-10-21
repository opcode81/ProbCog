/*******************************************************************************
 * Copyright (C) 2007-2012 Dominik Jain.
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
package probcog.srl.directed;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.srl.BooleanDomain;
import probcog.srl.Database;
import probcog.srl.RealDomain;
import probcog.srl.RelationKey;
import probcog.srl.Signature;
import probcog.srl.directed.learning.CPTLearner;
import probcog.srl.directed.learning.DomainLearner;
import probcog.srl.taxonomy.Concept;
import probcog.srl.taxonomy.Taxonomy;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.util.FileUtil;
import edu.tum.cs.util.StringTool;

/**
 * Advanced Bayesian Logical (ABL) Model
 * 
 * This class contains reading and writing methods that are specific to an implementation
 * of a relational belief network.  
 * 
 * @author Dominik Jain
 */
public class ABLModel extends RelationalBeliefNetwork {
	
	protected File networkFile = null;
	protected File[] declsFiles = null;
	
	public static Pattern regexFunctionName = Pattern.compile("[\\w]+"); // NOTE: should actually start with lower-case (because of Prolog compatibility), but left this way for backward comp. with older models
	public static Pattern regexTypeName = regexFunctionName;
	public static Pattern regexEntity = Pattern.compile("(?:[a-zA-Z][\\w]+|[0-9]+(?:\\.[0-9]+)?)");
	
	/**
	 * constructs a model by obtaining the node data from a fragment
	 * network and declarations from one or more files.
	 * 
	 * @param declarationsFiles
	 * @param networkFile
	 *            a fragment network file
	 * @throws Exception
	 */
	public ABLModel(String[] declarationsFiles, String networkFile) throws Exception {		
		init(declarationsFiles, networkFile);
	}
	
	/**
	 * constructs a BLOG model by obtaining the node data from a Bayesian
	 * network template and function signatures from a BLOG file.
	 * 
	 * @param declarationsFile
	 * @param networkFile
	 * @throws Exception
	 */
	public ABLModel(String declarationsFile, String networkFile) throws Exception {
		String[] decls = null;
		if(declarationsFile != null)
			decls = new String[] { declarationsFile };
		init(decls, networkFile);
	}

	public ABLModel(String declarationsFile) throws Exception {
		if(declarationsFile == null)
			throw new Exception("Declarations file cannot be null");
		init(new String[]{ declarationsFile }, null);
	}
	
	public static boolean isValidEntityName(String s) {
		return regexEntity.matcher(s).matches();
	}
	
	public static boolean isValidFunctionName(String s) {
		return regexFunctionName.matcher(s).matches();
	}

	public static boolean isValidTypeName(String s) {
		return regexTypeName.matcher(s).matches();
	}

	private void init(String[] declarationsFiles, String networkFile) throws Exception {
		if(networkFile != null)
			this.networkFile = new File(networkFile);
		boolean guessedSignatures = true;
		if(declarationsFiles != null) {
			declsFiles = new File[declarationsFiles.length];
			for(int i = 0; i < declarationsFiles.length; i++)
				declsFiles[i] = new File(declarationsFiles[i]).getAbsoluteFile();
			String content = readBlogContent(declsFiles);
			readDeclarations(content);
			guessedSignatures = false;
		}
		if(this.networkFile == null)
			throw new Exception("No fragment network was given");
		initNetwork(this.networkFile);
		if(guessedSignatures)
			guessSignatures();
		else
			checkSignatures();
	}

		
	protected void readDeclarations(String decls) throws Exception {
		// remove comments
		Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = comments.matcher(decls);
		decls = matcher.replaceAll("");

		// read line by line
		String[] lines = decls.split("\n");
		for (String line : lines) {
			line = line.trim();
			if (line.length() == 0)
				continue;
			if (!readDeclaration(line))
				if (!line.contains("~"))
					throw new Exception("Could not interpret the line '" + line	+ "'");
		}
	}

	protected boolean readDeclaration(String line) throws Exception {
		// function signature
		// TODO: logical Boolean required - split this into random / logical w/o Boolean / utility?
		if(line.startsWith("random") || line.startsWith("logical") || line.startsWith("utility")) {
			Pattern pat = Pattern.compile("(random|logical|utility)\\s+(\\w+)\\s+(\\w+)\\s*\\((.*)\\)\\s*;?", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pat.matcher(line);
			if (matcher.matches()) {
				boolean isLogical = matcher.group(1).equals("logical");
				boolean isUtility = matcher.group(1).equals("utility");
				String retType = matcher.group(2);
				String[] argTypes = matcher.group(4).trim().split("\\s*,\\s*");
				Signature sig = new Signature(matcher.group(3), retType, argTypes, isLogical, isUtility);				
				addSignature(sig);
				// functions declared as logical are always given (either implicitly through the closed-world assumption which assumes false)
				// or explicitly (in the explicit case, we do not insist that the variable must be Boolean, which is why we do not throw the exception).
				//if(isLogical && !sig.isBoolean())
				//	throw new Exception("Function '" + sig.functionName + "' was declared as logical but isn't a Boolean function");
				// ensure types used in signature exist, adding them if necessary
				addType(sig.returnType, false);
				for(String t : sig.argTypes)
					addType(t, false);				
				return true;
			}
			return false;
		}
		
		// obtain guaranteed domain elements
		if (line.startsWith("guaranteed")) {
			Pattern pat = Pattern.compile("guaranteed\\s+(\\w+)\\s+(.*?)\\s*;?");
			Matcher matcher = pat.matcher(line);
			if (matcher.matches()) {
				String domName = matcher.group(1);
				String[] elems = matcher.group(2).split("\\s*,\\s*");
				elems = makeDomainElements(elems);
				guaranteedDomElements.put(domName, Arrays.asList(elems));
				return true;
			}
			return false;
		}
		
		// read functional dependencies among relation arguments
		if (line.startsWith("relationKey") || line.startsWith("RelationKey")) {
			Pattern pat = Pattern.compile("[Rr]elationKey\\s+(\\w+)\\s*\\((.*)\\)\\s*;?");
			Matcher matcher = pat.matcher(line);
			if (matcher.matches()) {
				String relation = matcher.group(1);
				String[] arguments = matcher.group(2).trim().split("\\s*,\\s*");
				addRelationKey(new RelationKey(relation, arguments));
				return true;
			}
			return false;
		}
		
		// read type information
		if (line.startsWith("type") || line.startsWith("Type")) {
			Pattern pat = Pattern.compile("[Tt]ype\\s+(.*?)\\s*;?$");
			Matcher matcher = pat.matcher(line);
			Pattern typeDecl = Pattern.compile("(\\w+)(?:\\s+isa\\s+(\\w+))?");
			if (matcher.matches()) {
				String[] decls = matcher.group(1).split("\\s*,\\s*");
				for (String d : decls) {
					Matcher m = typeDecl.matcher(d);
					if(m.matches()) {						
						Concept c = addType(m.group(1), true);
						if (m.group(2) != null) {
							Concept parent = addType(m.group(2), false);
							c.setParent(parent);
						}
					}
					else
						throw new Exception("The type declaration '" + d + "' is invalid");					
				}
				return true;
			}
			return false;
		}
		
		// prolog rule
		if (line.startsWith("prolog")) {
			String rule = line.substring(6).trim();
			if(rule.endsWith(";"))
				rule = rule.substring(0, rule.length()-1);
			if(!rule.endsWith("."))
				rule += ".";
			prologRules.add(rule);
			return true;
		}
		
		// combining rule
		if(line.startsWith("combining-rule")) {
			Pattern pat = Pattern.compile("combining-rule\\s+(\\w+)\\s+([-\\w]+)\\s*;?");
			Matcher matcher = pat.matcher(line);
			if(matcher.matches()) {
				String function = matcher.group(1);
				String strRule = matcher.group(2);
				Signature sig = getSignature(function);
				CombiningRule rule;
				if(sig == null) 
					throw new Exception("Defined combining rule for unknown function '" + function + "'");
				try {
					rule = CombiningRule.fromString(strRule);
				}
				catch(IllegalArgumentException e) {
					Vector<String> v = new Vector<String>();
					for(CombiningRule cr : CombiningRule.values()) 
						v.add(cr.stringRepresention);
					throw new Exception("Invalid combining rule '" + strRule + "'; valid options: " + StringTool.join(", ", v));
				}
				this.combiningRules.put(function, rule);
				return true;
			}
		}
		
		// declaration of uniform default distribution if no fragment applicable
		if(line.startsWith("uniform-default")) {
			Pattern pat = Pattern.compile("uniform-default\\s+([-\\w]+(?:\\s*,\\s*[-\\w]+)*)\\s*;?");
			Matcher matcher = pat.matcher(line);
			if(matcher.matches()) {
				String[] functions = matcher.group(1).split("\\s*,\\s*");
				for(String f : functions)
					this.uniformDefaultFunctions.add(f);
			}
			return true;
		}
		
		// fragment network file reference
		if(line.startsWith("fragments")) {
			Pattern pat = Pattern.compile("fragments\\s+([^;\\s]+)\\s*;?");
			Matcher matcher = pat.matcher(line);			
			if(matcher.matches()) {
				String filename = matcher.group(1);
				File f = findReferencedFile(filename);
				if(f == null)
					throw new Exception("Declared fragments file " + filename + " could not be found");					
				if(networkFile != null) { // if we already have another network file, then the one that is declared here is not used
					if(!networkFile.getAbsoluteFile().equals(f.getAbsoluteFile())) 
						System.err.println("Notice: Declared network file " + filename + " is overridden by " + networkFile);
					return true;			
				}				
				networkFile = f;
				return true;
			}
		}
		return false;
	}
	
	public static String[] makeDomainElements(String[] elems) {
		// handle "i..j" -> list of integers from i to j
		Vector<String> vElems = null;
		for(int i = 0; i < elems.length; i++) {
			String item = elems[i];
			if(item.contains("..")) {
				if(vElems == null) {					
					vElems = new Vector<String>();
					for(int j = 0; j < i; j++)
						vElems.add(elems[j]);
				}
				String[] strBounds = item.split("\\.\\.");
				Integer from = Integer.parseInt(strBounds[0]);
				Integer to = Integer.parseInt(strBounds[1]);
				for(Integer k = from; k <= to; k++)
					vElems.add(k.toString());
			}
			else {
				if(vElems != null)
					vElems.add(item);					
			}
		}
		if(vElems != null)
			return vElems.toArray(new String[vElems.size()]);
		return elems;
	}
	
	protected File findReferencedFile(String filename) {
		File f = new File(filename).getAbsoluteFile();
		if(f.exists())			
			return f;
		else {
			for(File parentFile : this.declsFiles) {
				f = new File(parentFile.getParentFile().getAbsoluteFile(), filename);
				if(f.exists())
					return f;
			}
		}
		return null;
	}
	
	/**
	 * adds a concept for a type to the taxonomy unless it is already present
	 * @param typeName the name of the type
	 * @param explicitlyDeclared whether the type is to be added due to its explicit declaration (all other type creations must issue a warning!)
	 * @return the taxonomy object for the given
	 */
	protected Concept addType(String typeName, boolean explicitlyDeclared) {
		if(BooleanDomain.isBooleanType(typeName) || RealDomain.isRealType(typeName))
			return null;
		if(taxonomy == null) 
			taxonomy = new Taxonomy();
		Concept c = taxonomy.getConcept(typeName);
		if(c == null) {
			taxonomy.addConcept(c = new Concept(typeName));
			if(!explicitlyDeclared)
				System.err.println("Warning: The type '" + typeName + "' was not explicitly declared before it was first used; implicitly adding it to the taxonomy...");
		}
		return c;
	}

	/**
	 * read the contents of one or more (BLOG) files into a single string
	 * 
	 * @param files
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	protected String readBlogContent(File[] files)
			throws FileNotFoundException, IOException {
		// read the blog files
		StringBuffer buf = new StringBuffer();
		for (File blogFile : files) {
			buf.append(FileUtil.readTextFile(blogFile));
			buf.append('\n');
		}
		return buf.toString();
	}

	/**
	 * generates the ground Bayesian network for the template network that this
	 * model represents, instantiating it with the guaranteed domain elements
	 * 
	 * @return
	 * @throws Exception
	 * @deprecated no longer maintained; for BLNs superseded by the respective grounding process  
	 */
	public BeliefNetworkEx getGroundBN() throws Exception {
		// create a new Bayesian network
		BeliefNetworkEx gbn = new BeliefNetworkEx();
		// add nodes in topological order
		int[] order = this.getTopologicalOrder();
		for (int i = 0; i < order.length; i++) { // for each template node (in
			// topological order)
			RelationalNode node = getRelationalNode(order[i]);
			// get all possible argument groundings
			Signature sig = getSignature(node.functionName);
			if (sig == null)
				throw new Exception("Could not retrieve signature for node "
						+ node.functionName);
			Vector<String[]> argGroundings = groundParams(sig);
			// create a new node for each grounding with the same domain and CPT
			// as the template node
			for (String[] args : argGroundings) {
				String newName = Signature.formatVarName(node.functionName,
						args);
				BeliefNode newNode = new BeliefNode(newName, node.node
						.getDomain());
				gbn.addNode(newNode);
				// link from all the parent nodes
				String[] parentNames = getParentVariableNames(node, args);
				for (String parentName : parentNames) {
					BeliefNode parent = gbn.getNode(parentName);
					gbn.bn.connect(parent, newNode);
				}
				// transfer the CPT (the entries for the new node may not be in
				// the same order so determine the appropriate mapping)
				// TODO this assumes that a function name occurs only in one
				// parent
				CPF newCPF = newNode.getCPF(), oldCPF = node.node.getCPF();
				BeliefNode[] oldProd = oldCPF.getDomainProduct();
				BeliefNode[] newProd = newCPF.getDomainProduct();
				int[] old2newindex = new int[oldProd.length];
				for (int j = 0; j < oldProd.length; j++) {
					for (int k = 0; k < newProd.length; k++)
						if (RelationalNode.extractFunctionName(
								newProd[k].getName()).equals(
								RelationalNode.extractFunctionName(oldProd[j]
										.getName())))
							old2newindex[j] = k;
				}
				for (int j = 0; j < oldCPF.size(); j++) {
					int[] oldAddr = oldCPF.realaddr2addr(j);
					int[] newAddr = new int[oldAddr.length];
					for (int k = 0; k < oldAddr.length; k++)
						newAddr[old2newindex[k]] = oldAddr[k];
					newCPF.put(newCPF.addr2realaddr(newAddr), oldCPF.get(j));
				}
			}
		}
		return gbn;
	}

	/**
	 * gets a list of lists of constants representing all possible combinations
	 * of elements of the given domains (domNames)
	 * 
	 * @param domNames
	 *            a list of domain names
	 * @param setting
	 *            the current setting (initially empty) - same length as
	 *            domNames
	 * @param idx
	 *            the index of the domain from which to choose next
	 * @param ret
	 *            the vector in which all settings shall be stored
	 * @throws Exception
	 */
	protected void groundParams(String[] domNames, String[] setting, int idx,
			Vector<String[]> ret) throws Exception {
		if (idx == domNames.length) {
			ret.add(setting.clone());
			return;
		}
		Collection<String> elems = guaranteedDomElements.get(domNames[idx]);
		if (elems == null) {
			throw new Exception("No guaranteed domain elements for "
					+ domNames[idx]);
		}
		for (String elem : elems) {
			setting[idx] = elem;
			groundParams(domNames, setting, idx + 1, ret);
		}
	}

	protected Vector<String[]> groundParams(Signature sig) throws Exception {
		Vector<String[]> ret = new Vector<String[]>();
		groundParams(sig.argTypes, new String[sig.argTypes.length], 0, ret);
		return ret;
	}

	public void write(PrintStream out) throws Exception {
		BeliefNode[] nodes = bn.getNodes();

		// write declarations for types, guaranteed domain elements and
		// functions
		writeDeclarations(out);

		// write conditional probability distributions
		// NOTE: These declarations are only included for compatibility with BLOG, they are not necessary otherwise, as distributions are read from fragment networks
		// TODO handle decision parents properly by using if-then-else?
		for (RelationalNode relNode : getRelationalNodes()) {
			if (relNode.isAuxiliary)
				continue;
			CPF cpf = nodes[relNode.index].getCPF();
			BeliefNode[] deps = cpf.getDomainProduct();
			Discrete[] domains = new Discrete[deps.length];
			StringBuffer args = new StringBuffer();
			int[] addr = new int[deps.length];
			for (int j = 0; j < deps.length; j++) {
				if (deps[j].getType() == BeliefNode.NODE_DECISION) 
					// ignore decision nodes (they are not dependencies because
					// they are assumed to be true)
					continue;
				if (j > 0) {
					if (j > 1)
						args.append(", ");
					args.append(getRelationalNode(deps[j]).getCleanName());
				}
				domains[j] = (Discrete) deps[j].getDomain();
			}
			Vector<String> lists = new Vector<String>();
			getCPD(lists, cpf, domains, addr, 1);
			out.printf("%s ~ TabularCPD[%s](%s);\n", relNode.getCleanName(),
					StringTool.join(",", lists.toArray(new String[0])), args
							.toString());
		}
	}

	protected void writeDeclarations(PrintStream out) {
		if(this.networkFile != null) {
			out.printf("fragments %s;\n\n", this.networkFile.toString());
		}
		
		// write type decls
		for(Concept c : this.taxonomy.getConcepts()) {
			if(c.parent == null)
				out.printf("type %s;\n", c.name);
			else
				out.printf("type %s isa %s;\n", c.name, c.parent.name);
		}
		out.println();

		// write domains
		for(Entry<String, ? extends Collection<String>> e : guaranteedDomElements.entrySet()) {
			out.println("guaranteed " + e.getKey() + " "  + StringTool.join(", ", e.getValue()) + ";");
		}
		out.println();

		// signatures
		for(Signature sig : getSignatures()) {
			out.printf("%s %s %s(%s);\n", sig.isLogical ? "logical" : "random", sig.returnType, sig.functionName, StringTool.join(", ", sig.argTypes));
		}
		out.println();
		
		// relation keys
		for(Collection<RelationKey> c : this.relationKeys.values())
			for(RelationKey relKey : c)
				out.println(relKey.toString());
		out.println();
		
		// combining rules
		for(Entry<String, CombiningRule> e : this.combiningRules.entrySet()) {
			out.printf("combining-rule %s %s;\n", e.getKey(), e.getValue().stringRepresention);
		}
	}

	protected void getCPD(Vector<String> lists, CPF cpf, Discrete[] domains, int[] addr, int i) {
		if (i == addr.length) {
			StringBuffer sb = new StringBuffer();
			sb.append('[');
			for (int j = 0; j < domains[0].getOrder(); j++) {
				addr[0] = j;
				int realAddr = cpf.addr2realaddr(addr);
				double value = ((ValueDouble) cpf.get(realAddr)).getValue();
				if (j > 0)
					sb.append(',');
				sb.append(value);
			}
			sb.append(']');
			lists.add(sb.toString());
		} 
		else {
			// go through all possible parent-child configurations
			BeliefNode[] domProd = cpf.getDomainProduct();
			if (domProd[i].getType() == BeliefNode.NODE_DECISION) // for decision nodes, always assume true
				addr[i] = 0;
			else {
				for (int j = 0; j < domains[i].getOrder(); j++) {
					addr[i] = j;
					getCPD(lists, cpf, domains, addr, i + 1);
				}
			}
		}
	}
	
	public void setNetworkFilename(String networkFilename) {
		this.networkFile = new File(networkFilename);
	}
	
	public static void main(String[] args) {
		try {
			String bifFile = "abl/kitchen-places/actseq.xml";
			ABLModel bn = new ABLModel(new String[] { "abl/kitchen-places/actseq.abl" }, bifFile);
			String dbFile = "abl/kitchen-places/train.blogdb";
			// read the training database
			System.out.println("Reading data...");
			Database db = new Database(bn);
			db.readBLOGDB(dbFile);
			System.out.println("  " + db.getEntries().size()
					+ " variables read.");
			// learn domains
			if (true) {
				System.out.println("Learning domains...");
				DomainLearner domLearner = new DomainLearner(bn);
				domLearner.learn(db);
				domLearner.finish();
			}
			// learn parameters
			System.out.println("Learning parameters...");
			CPTLearner cptLearner = new CPTLearner(bn);
			cptLearner.learnTyped(db, true, true);
			cptLearner.finish();
			System.out.println("Writing XML-BIF output...");
			bn.saveXMLBIF(bifFile);
			if (true) {
				System.out.println("Showing Bayesian network...");
				bn.show();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
