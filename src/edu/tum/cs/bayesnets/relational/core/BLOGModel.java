package edu.tum.cs.bayesnets.relational.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.relational.core.RelationalNode.Signature;

public class BLOGModel extends RelationalBeliefNetwork {
	
	protected HashMap<String, String[]> guaranteedDomElements;
	protected String blogContents;
	
	public static String readTextFile(String filename) throws FileNotFoundException, IOException {
		File inputFile = new File(filename);
		FileReader fr = new FileReader(inputFile);
		char[] cbuf = new char[(int)inputFile.length()];
		fr.read(cbuf);
		String content = new String(cbuf);
		fr.close();
		return content;
	}

	/**
	 * constructs a BLOG model by obtaining the node data from a Bayesian network template and function signatures from one or more BLOG files.
	 * @param blogFiles
	 * @param xmlbifFile
	 * @throws Exception
	 */
	public BLOGModel(String[] blogFiles, String xmlbifFile) throws Exception {
		super(xmlbifFile);
		guaranteedDomElements = new HashMap<String, String[]>();
		
		// read the blog files
		String blog = readBlogContent(blogFiles);
		this.blogContents = blog;
		
		// obtain signatures from the blog file				
		Pattern pat = Pattern.compile("random\\s+(\\w+)\\s+(\\w+)\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pat.matcher(blog);
		while(matcher.find()) {
			String retType = matcher.group(1);
			String[] argTypes = matcher.group(3).trim().split("\\s*,\\s*");
			Signature sig = new Signature(matcher.group(2), retType, argTypes);
			addSignature(matcher.group(2), sig);
		}
		
		getGuaranteedDomainElements(blog);
		
		checkSignatures();
	}
	
	/**
	 * read the contents of one or more (BLOG) files into a single string
	 * @param files
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	protected String readBlogContent(String[] files) throws FileNotFoundException, IOException {
		// read the blog files
		StringBuffer buf = new StringBuffer();
		for(String blogFile : files) {
			buf.append(readTextFile(blogFile));
			buf.append('\n');
		}
		return buf.toString();
	}
	
	/**
	 * constructs a BLOG model by obtaining the node data from a Bayesian network template and function signatures from a BLOG file.
	 * @param blogFile
	 * @param xmlbifFile
	 * @throws Exception
	 */
	public BLOGModel(String blogFile, String xmlbifFile) throws Exception {
		this(new String[]{blogFile}, xmlbifFile);
	}
	
	/**
	 * reads the guaranteed domain elements listed in the given contents of a BLOG file and stores them in the guaranteedDomElements member
	 * @param blogContent
	 */
	protected void getGuaranteedDomainElements(String blogContent) {
		// obtain guaranteed domain elements
		Pattern pat = Pattern.compile("guaranteed\\s+(\\w+)\\s+(.*)\\s*;");
		Matcher matcher = pat.matcher(blogContent);
		while(matcher.find()) {
			String domName = matcher.group(1);
			String[] elems = matcher.group(2).split("\\s*,\\s*");
			guaranteedDomElements.put(domName, elems);
		}	
	}
	
	/**
	 * constructs a BLOG model from a Bayesian network template. The function signatures are derived from node/parameter names.
	 * @param xmlbifFile
	 * @throws Exception
	 */
	public BLOGModel(String xmlbifFile) throws Exception {
		super(xmlbifFile);
		this.guessSignatures();
	}
	
	/**
	 * generates the ground Bayesian network for the template network that this model represents, instantiating it with the guaranteed domain elements 
	 * @return
	 * @throws Exception
	 */
	public BeliefNetworkEx getGroundBN() throws Exception {
		// create a bew Bayesian network 
		BeliefNetworkEx gbn = new BeliefNetworkEx();
		// add nodes in topological order
		int[] order = this.getTopologicalOrder();
		for(int i = 0; i < order.length; i++) { // for each template node (in topological order)
			RelationalNode node = getRelationalNode(order[i]);
			// get all possible argument groundings
			Signature sig = getSignature(node.functionName);
			if(sig == null)
				throw new Exception("Could not retrieve signature for node " + node.functionName);
			Vector<String[]> argGroundings = groundParams(sig); 
			// create a new node for each grounding with the same domain and CPT as the template node
			for(String[] args : argGroundings) {
				String newName = RelationalNode.formatName(node.functionName, args);
				BeliefNode newNode = new BeliefNode(newName, node.node.getDomain());
				gbn.addNode(newNode);
				// link from all the parent nodes
				String[] parentNames = getParentVariableNames(node, args);
				for(String parentName : parentNames) {
					BeliefNode parent = gbn.getNode(parentName);
					gbn.bn.connect(parent, newNode);
				}
				// transfer the CPT (the entries for the new node may not be in the same order so determine the appropriate mapping)
				CPF newCPF = newNode.getCPF(), oldCPF = node.node.getCPF();
				BeliefNode[] oldProd = oldCPF.getDomainProduct();
				BeliefNode[] newProd = newCPF.getDomainProduct();
				int[] old2newindex = new int[oldProd.length];
				for(int j = 0; j < oldProd.length; j++) {
					for(int k = 0; k < newProd.length; k++)
						if(RelationalNode.extractFunctionName(newProd[k].getName()).equals(RelationalNode.extractFunctionName(oldProd[j].getName())))
							old2newindex[j] = k;
				}
				for(int j = 0; j < oldCPF.size(); j++) {
					int[] oldAddr = oldCPF.realaddr2addr(j);
					int[] newAddr = new int[oldAddr.length];
					for(int k = 0; k < oldAddr.length; k++)
						newAddr[old2newindex[k]] = oldAddr[k];
					newCPF.put(newCPF.addr2realaddr(newAddr), oldCPF.get(j));					
				}
			}
		}
		return gbn;
	}
	
	/**
	 * gets a list of lists of constants representing all possible combinations of elements of the given domains (domNames)
	 * @param domNames a list of domain names
	 * @param setting  the current setting (initially empty) - same length as domNames 
	 * @param idx  the index of the domain from which to choose next 
	 * @param ret  the vector in which all settings shall be stored
	 * @throws Exception 
	 */
	protected void groundParams(String[] domNames, String[] setting, int idx, Vector<String[]> ret) throws Exception {
		if(idx == domNames.length) {
			ret.add(setting.clone());
			return;
		}
		String[] elems = guaranteedDomElements.get(domNames[idx]);
		if(elems == null) {
			throw new Exception("No guaranteed domain elements for " + domNames[idx]);
		}
		for(String elem : elems) {
			setting[idx] = elem;
			groundParams(domNames, setting, idx+1, ret);
		}
	}
	
	protected Vector<String[]> groundParams(Signature sig) throws Exception {
		Vector<String[]> ret = new Vector<String[]>();
		groundParams(sig.argTypes, new String[sig.argTypes.length], 0, ret);
		return ret;
	}
	
	public void write(PrintStream out) throws Exception {
		BeliefNode[] nodes = bn.getNodes();
		
		// write type decls 
		Set<String> types = new HashSet<String>();
		for(RelationalNode node : this.getRelationalNodes()) {
			if(node.isBuiltInPred())
				continue;
			Signature sig = this.getSignature(node.functionName);
			Discrete domain = (Discrete)node.node.getDomain();
			if(!types.contains(sig.returnType) && !sig.returnType.equals("Boolean")) {
				if(!isBooleanDomain(domain)) {
					types.add(sig.returnType);
					out.printf("Type %s;\n", sig.returnType);
				}
				else
					sig.returnType = "Boolean";
			}
			for(String t : sig.argTypes) {
				if(!types.contains(t)) {
					types.add(t);
					out.printf("Type %s;\n", t);
				}
			}
		}
		out.println();
		
		// write domains
		Set<String> handledDomains = new HashSet<String>();
		for(RelationalNode node : this.getRelationalNodes()) {
			if(node.isBuiltInPred()) 
				continue;
			Discrete domain = (Discrete)node.node.getDomain();
			Signature sig = getSignature(node.functionName);
			if(!sig.returnType.equals("Boolean")) {
				String t = sig.returnType;
				if(!handledDomains.contains(t)) {
					handledDomains.add(t);
					out.print("guaranteed " + t + " ");			
					for(int j = 0; j < domain.getOrder(); j++) {
						if(j > 0) out.print(", ");
						out.print(domain.getName(j));
					}
					out.println(";");
				}
			}
		}
		out.println();
		
		// functions
		for(RelationalNode node : this.getRelationalNodes()) {
			if(node.isBuiltInPred())
				continue;
			Signature sig = getSignature(node.functionName);
			out.printf("random %s %s(%s);\n", sig.returnType, node.functionName, RelationalNode.join(", ", sig.argTypes));			
		}
		out.println();
		
		// CPTs
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode relNode = getRelationalNode(nodes[i]); 
			if(relNode.isAuxiliary) 
				continue;
			CPF cpf = nodes[i].getCPF();
			BeliefNode[] deps = cpf.getDomainProduct();
			Discrete[] domains = new Discrete[deps.length];
			StringBuffer args = new StringBuffer();
			int[] addr = new int[deps.length];
			for(int j = 0; j < deps.length; j++) {
				if(j > 0) {
					args.append(getRelationalNode(deps[j]).getCleanName());
					if(j < deps.length-1)
						args.append(", ");
				}
				domains[j] = (Discrete)deps[j].getDomain();
			}
			Vector<String> lists = new Vector<String>();
			getCPD(lists, cpf, domains, addr, 1);
			out.printf("%s ~ TabularCPD[%s](%s);\n", relNode.getCleanName(), RelationalNode.join(",", lists.toArray(new String[0])), args.toString());
		}
	}
	
	protected void getCPD(Vector<String> lists, CPF cpf, Discrete[] domains, int[] addr, int i) {
		if(i == addr.length) {			
			StringBuffer sb = new StringBuffer();
			sb.append('[');
			for(int j = 0; j < domains[0].getOrder(); j++) {
				addr[0] = j;
				int realAddr = cpf.addr2realaddr(addr);
				double value = ((ValueDouble)cpf.get(realAddr)).getValue();
				if(j > 0)
					sb.append(',');
				sb.append(value);
			}
			sb.append(']');
			lists.add(sb.toString());
		}
		else {
			for(int j = 0; j < domains[i].getOrder(); j++) {
				addr[i] = j;
				getCPD(lists, cpf, domains, addr, i+1);
			}
		}
	}
}
