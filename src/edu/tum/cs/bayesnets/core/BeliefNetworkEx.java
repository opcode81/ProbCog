package edu.tum.cs.bayesnets.core;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.streams.*;
import edu.ksu.cis.bnj.ver3.core.*;
import edu.ksu.cis.bnj.ver3.core.values.*;
import edu.ksu.cis.bnj.ver3.inference.approximate.sampling.ForwardSampling;
import edu.ksu.cis.bnj.ver3.inference.exact.Pearl;
import edu.ksu.cis.bnj.ver3.streams.OmniFormatV1_Reader;
import edu.ksu.cis.bnj.ver3.streams.OmniFormatV1_Writer;
import edu.ksu.cis.util.graph.algorithms.TopologicalSort;
import edu.tum.cs.bayesnets.core.io.*;

/**
 * An instance of class BeliefNetworkEx represents a full Bayesian Network.
 * It is a wrapper for BNJ's BeliefNetwork class with extended functionality.
 * BeliefNetwork could not simply be extended by inheritance because virtually all members are
 * declared private. Therefore, BeliefNetworkEx has a public member bn, which is an instance of
 * BeliefNetwork. 
 * 
 * @author Dominik Jain
 *
 */
public class BeliefNetworkEx {
	/**
	 * the BNJ BeliefNetwork object that is wrapped by the instance of class BeliefNetworkEx.
	 * When using BNJ directly, you may need this; or you may want to use the methods of BeliefNetwork to perform
	 * an operation on the network that BeliefNetworkEx does not wrap.  
	 */
	public BeliefNetwork bn;
	
	/**
	 * the format constant for the XML-BIF format
	 */
	public static final int FORMAT_XMLBIF = 0;
	/**
	 * the format constant for the PMML-based format (PMML version 3.0 with custom extensions) 
	 */
	public static final int FORMAT_PMML = 1;
	
	/**
	 * constructs a BeliefNetworkEx object from a BNJ BeliefNetwork object
	 * @param bn	the BNJ BeliefNetwork object
	 */
	public BeliefNetworkEx(BeliefNetwork bn) {
		this.bn = bn;
	}
	
	/**
	 * constructs a BeliefNetworkEx object from a saved XML-BIF file
	 * @param xmlbifFile	the name of the XML-BIF file to load the network from
	 */
	public BeliefNetworkEx(String xmlbifFile) throws FileNotFoundException {
		this.bn = load(xmlbifFile, new Converter_xmlbif());
	}
	
	/**
	 * constructs a BeliefNetworkEx object by loading the network from a file
	 * @param filename		the name of the file that contains the network data
	 * @param format		the format specifier; use one of the format constants (i.e. FORMAT_XMLBIF, FORMAT_PMML)
	 */
	public BeliefNetworkEx(String filename, int format) throws Exception, FileNotFoundException {
		switch(format) {
		case FORMAT_XMLBIF:
			this.bn = load(filename, new Converter_xmlbif());
			break;
		case FORMAT_PMML:
			this.bn = load(filename, new Converter_pmml());
			break;
		default:
			throw new Exception("Can't load - unknown format!");
		}		
	}
	
	/**
	 * constructs an empty network. Use methods addNode and connect to define the network structure.  
	 */ 
	public BeliefNetworkEx() {
		this.bn = new BeliefNetwork();
	}
	
	/**
	 * adds a node to the network
	 * @param node	the node that is to be added
	 */
	public void addNode(BeliefNode node) {
		bn.addBeliefNode(node);
	}
	
	/**
	 * adds a node with the given name and the standard discrete domain {True, False} to the network
	 * @param name	the name of the node
	 * @return		a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name) {
		return addNode(name, new Discrete(new String[]{"True", "False"}));
	}
	
	/**
	 * adds a node with the given name and domain to the network
	 * @param name		the name of the node
	 * @param domain	the node's domain (usually an instance of BNJ's class Discrete)
	 * @return			a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name, Domain domain) {
		BeliefNode node = new BeliefNode(name, domain);
		bn.addBeliefNode(node);
		return node;
	}
	
	/**
	 * adds an edge to the network, i.e. a dependency
	 * @param node1		the name of the node that influences another
	 * @param node2		the name of node that is influenced
	 * @throws Exception	if either of the node names are invalid
	 */
	public void connect(String node1, String node2) throws Exception {
		BeliefNode n1 = getNode(node1);
		BeliefNode n2 = getNode(node2);
		if(n1 == null || n2 == null)
			throw new Exception("One of the node names is invalid!");		
		bn.connect(n1, n2);
	}
	
	/**
	 * retrieves the node with the given name 
	 * @param name		the name of the node
	 * @return			a reference to the node (or null if there is no node with the given name)
	 */
	public BeliefNode getNode(String name) {
		int idx = getNodeIndex(name);
		if(idx == -1)
			return null;
		return bn.getNodes()[idx];
	}
	
	/**
	 * get the index (into the BeliefNetwork's array of nodes) of the node with the given name
	 * @param name	the name of the node
	 * @return		the index of the node (or -1 if there is no node with the given name)
	 */
	public int getNodeIndex(String name) {
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++)
			if(nodes[i].getName().equals(name))
				return i;
		return -1;		
	}
	
	/**
	 * sets evidence for one of the network's node
	 * @param nodeName		the name of the node for which evidence is to be set
	 * @param outcome		the outcome, which must be in compliance with the node's domain
	 * @throws Exception	if the node name does not exist in the network or the outcome is not valid for the node's domain
	 */
	public void setEvidence(String nodeName, String outcome) throws Exception {
		BeliefNode node = getNode(nodeName);
		if(node == null)
			throw new Exception("Invalid node reference: " + nodeName);
		Discrete domain = (Discrete) node.getDomain();
		int idx = domain.findName(outcome);
		if(idx == -1)
			throw new Exception("Outcome " + outcome + " not in domain of " + nodeName);
		node.setEvidence(new DiscreteEvidence(idx));		
	}
	
	/**
	 * calculates a probability Pr[X=x, Y=y, ... | E=e, F=f, ...]
	 * @param queries		an array of 2-element string arrays (variable, outcome)
	 * 						that represents the conjunction "X=x AND Y=y AND ...";
	 * @param evidences		the conjunction of evidences, specified in the same way
	 * @return				the calculated probability
	 * @throws Exception
	 */
	public double getProbability(String[][] queries, String[][] evidences) throws Exception {
		// queries with only one query variable (i.e. Pr[X | A,B,...]) can be solved directly
		// ... for others, recursion is necessary
		if(queries.length == 1) { 
			// remove any previous evidence
			BeliefNode[] nodes = bn.getNodes();			
			for(int i = 0; i < nodes.length; i++)
				nodes[i].setEvidence(null);
			// set new evidence
			if(evidences != null)
				for(int i = 0; i < evidences.length; i++) {
					setEvidence(evidences[i][0], evidences[i][1]);				
				}
			// run inference
			Pearl inf = new Pearl();		
			inf.run(this.bn);
			// return result
			BeliefNode node = getNode(queries[0][0]);
			CPF cpf = inf.queryMarginal(node);
			BeliefNode[] dp = cpf.getDomainProduct();
			boolean done = false;
			int[] addr = cpf.realaddr2addr(0);
			while(!done) {
				for (int i = 0; i < addr.length; i++)
					if(dp[i].getDomain().getName(addr[i]).equals(queries[0][1])) {
						ValueDouble v = (ValueDouble) cpf.get(addr);
						return v.getValue();						
					}
				done = cpf.addOne(addr);
			}
			throw new Exception("Outcome not in domain!");
			//inf.printResults();
		}
		else { // Pr[A,B,C,D | E] = Pr[A | B,C,D,E] * Pr[B,C,D | E]
			String[][] _queries = new String[1][2];
			String[][] _queries2 = new String[queries.length-1][2];
			_queries[0] = queries[0];
			int numEvidences = evidences == null ? 0 : evidences.length;
			String[][] _evidences = new String[numEvidences+queries.length-1][2];
			int idx = 0;
			for(int i = 1; i < queries.length; i++, idx++) {
				_evidences[idx] = queries[i];
				_queries2[idx] = queries[i];
			}
			for(int i = 0; i < numEvidences; i++, idx++)
				_evidences[idx] = evidences[i];
			return getProbability(_queries, _evidences) * getProbability(_queries2, evidences);			
		}
	}	
		
	protected void printProbabilities(int node, Stack<String[]> evidence) throws Exception {
		BeliefNode[] nodes = bn.getNodes();
		if(node == nodes.length) {
			String[][] e = new String[evidence.size()][];
			evidence.toArray(e);
			double prob = getProbability(e, null);
			StringBuffer s = new StringBuffer();
			s.append(String.format("%6.2f%%  ", 100*prob));
			int i = 0;
			for(String[] pair : evidence) {
				if(i > 0)
					s.append(", ");
				s.append(String.format("%s=%s", pair[0], pair[1]));
				i++;
			}
			System.out.println(s);
			return;
		}
		Domain dom = nodes[node].getDomain();
		for(int i = 0; i < dom.getOrder(); i++) {
			evidence.push(new String[]{nodes[node].getName(), dom.getName(i)});
			printProbabilities(node+1, evidence);
			evidence.pop();
		}
	}
	
	public void printFullJoint() throws Exception {
		printProbabilities(0, new Stack<String[]>());
	}

	/**
	 * prints domain information for all nodes of the network to System.out
	 */
	public void printDomain() {
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			System.out.print(nodes[i].getName());
			Discrete domain = (Discrete)nodes[i].getDomain();
			System.out.print(" {");
			int c = domain.getOrder();
			for(int j = 0; j < c; j++) {
				if(j > 0) System.out.print(", ");
				System.out.print(domain.getName(j));
			}			
			System.out.println("}");
		}
	}
	
	/**
	 * static function for loading a Bayesian network into an instance of class BeliefNetwork
	 * @param filename					the file containing the network data	
	 * @param importer					an importer that is capable of understanding the file format
	 * @return							the loaded network in a new instance of class BeliefNetwork
	 * @throws FileNotFoundException
	 */
	public static BeliefNetwork load(String filename, Importer importer) throws FileNotFoundException {
		FileInputStream fis = new FileInputStream(filename);
		OmniFormatV1_Reader ofv1w = new OmniFormatV1_Reader();
		importer.load(fis, ofv1w);
		return ofv1w.GetBeliefNetwork(0);
	}
	
	/**
	 * static function for writing a Bayesian network to a file using a given exporter
	 * @param net						the network to be written
	 * @param filename					the file to write to
	 * @param exporter					an exporter for the desired file format
	 * @throws FileNotFoundException
	 */
	public static void save(BeliefNetwork net, String filename, Exporter exporter) throws FileNotFoundException {
		exporter.save(new FileOutputStream(filename));
		OmniFormatV1_Writer.Write(net, (OmniFormatV1)exporter);
	}
	
	/**
	 * writes the Bayesian network to a file with the given name using an exporter
	 * @param filename					the file to write to
	 * @param exporter					an exporter for the desired file format
	 * @throws FileNotFoundException
	 */
	public void save(String filename, Exporter exporter) throws FileNotFoundException {
		save(this.bn, filename, exporter);
	}

	/**
	 * writes the Bayesian network to a file with the given name in XML-BIF format
	 * @param filename					the file to write to
	 * @throws FileNotFoundException
	 */
	public void saveXMLBIF(String filename) throws FileNotFoundException {
		save(filename, new Converter_xmlbif());
	}
	
	/**
	 * writes the Bayesian network to a file with the given name in a PMML-based format
	 * @param filename					the file to write to
	 * @throws FileNotFoundException
	 */
	public void savePMML(String filename) throws FileNotFoundException {
		save(filename, new Converter_pmml());
	}
	
	/**
	 * sorts the domain of the node with the given name alphabetically (if numeric is false) or
	 * numerically (if numeric is true) - in ascending order
	 * @param nodeName		the name of the node whose domain is to be sorted
	 * @param numeric		whether to sort numerically or not. If numeric is true,
	 * 						all domain values are converted to double for sorting.
	 * 						If numeric is false, the values are simply sorted alphabetically. 
	 * @throws Exception	if the node name is invalid
	 */
	public void sortNodeDomain(String nodeName, boolean numeric) throws Exception {
		BeliefNode node = getNode(nodeName);
		if(node == null)
			throw new Exception("Node not found");
		Discrete domain = (Discrete)node.getDomain();
		int ord = domain.getOrder();
		String[] strings = new String[ord];
		if(!numeric) {			
			for(int i = 0; i < ord; i++)
				strings[i] = domain.getName(i);
			Arrays.sort(strings);			
		}
		else {
			double[] values = new double[ord];
			for(int i = 0; i < ord; i++)
				values[i] = Double.parseDouble(domain.getName(i));
			double[] sorted_values = values.clone();			
			Arrays.sort(sorted_values);			
			for(int i = 0; i < ord; i++)
				for(int j = 0; j < ord; j++)
					if(sorted_values[i] == values[j])
						strings[i] = domain.getName(j);			
		}
		bn.changeBeliefNodeDomain(node, new Discrete(strings));
	}
	
	/**
	 * returns the domain of the node with the given name
	 * @param nodeName	the name of the node for which the domain is to be returned
	 * @return			the domain of the node (usually instance of class Discrete)
	 * 					or null if the node name is invalid
	 */
	public Domain getDomain(String nodeName) {
		BeliefNode node = getNode(nodeName);
		if(node == null)
			return null;
		return node.getDomain();
	}

	/**
	 * shows the Bayesian Network in a BNJ editor window (without saving/loading capabilities)
	 */
	public void show() {
		edu.ksu.cis.bnj.gui.GUIWindow window = new edu.ksu.cis.bnj.gui.GUIWindow();
		window.register();		
		window.open(bn);
	}
	
	/**
	 * shows the Bayesian Network in a BNJ editor window,
	 * loading the BNJ plugins in the given directory so that
	 * saving and loading from within BNJ are possible. 
	 * @param pluginDir		a directory containing BNJ plugins 
	 */
	public void show(String pluginDir) {
		edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader.init(pluginDir);
		show();
	}
	
	/**
	 * helper function for queryShell that reads a list of comma-separated assignments "A=a,B=b,..."
	 * into an array [["A","a"],["B","b"],...]
	 * @param list
	 * @return
	 * @throws java.lang.Exception
	 */
	protected static String[][] readList(String list) throws java.lang.Exception {
		if(list == null)
			return null;
		String[] items = list.split(",");
		String[][] res = new String[items.length][2];
		for(int i = 0; i < items.length; i++) {
			res[i] = items[i].split("=");
			if(res[i].length != 2)
				throw new java.lang.Exception("syntax error!");
		}
		return res;
	}

	/**
	 * starts a shell that allows the user to query the network
	 */
	public void queryShell() {
		// output some usage information
		System.out.println("Domain:");
		printDomain();
		System.out.println("\nUsage: Pr[X=x, Y=y, ... | E=e, F=f, ...]   (X,Y: query vars;\n" +
						     "                                            E,F: evidence vars;\n" +
						     "                                            x,y,e,f: outcomes\n" + 
				             "       exit                                (close shell)");
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in)); 
	    for(;;) {	
			try {
				// get input query from stdin 
				System.out.print("\n> ");			
			    String input = br.readLine(); 
				
				if(input.equalsIgnoreCase("exit"))
					break;
			
				// parse expression...
				input = input.replaceAll("\\s+", "");
				Pattern p = Pattern.compile("Pr\\[([^\\]\\|]*)(?:\\|([^\\]]*))?\\]");		
				Matcher m = p.matcher(input);
				if(!m.matches()) {
					System.out.println("syntax error!");
				}
				else {
					String[][] queries = readList(m.group(1));
					String[][] evidences = readList(m.group(2));
					try {
						// evaluate and output result...
						double result = getProbability(queries, evidences);
						System.out.println(result);
					}
					catch(Exception e) {
						System.out.println(e.getMessage());
					}
				}
			}
			catch(IOException e) {
				System.err.println(e.getMessage());
			}
			catch(java.lang.Exception e) {
				System.out.println(e.getMessage());
			}
	    }
	}
	
	/**
	 * gets a topological ordering of the network's nodes  
	 * @return an array of integers containing node indices
	 */
	public int[] getTopologicalOrder() {
		TopologicalSort topsort = new TopologicalSort();
		topsort.execute(bn.getGraph());
		return topsort.alpha;
	}
	
	/**
	 * performs sampling on the network and returns a sample of the distribution represented by this Bayesian network; evidences that are set during sampling are removed
	 * afterwards in order to retain the original state of the network.
	 * @return a hashmap of (node name, string value) pairs representing the sample
	 * @param generator random number generator to use to generate sample (null to create one) 
	 * @throws Exception
	 */
	public HashMap<String,String> getSample(Random generator) throws Exception {
		if(generator == null)
			generator = new Random();
		HashMap<String,String> ret = new HashMap<String,String>();
		// perform topological sort to determine sampling order
		TopologicalSort topsort = new TopologicalSort();
		topsort.execute(bn.getGraph());
		int[] order = topsort.alpha;
		// sample
		BeliefNode[] nodes = bn.getNodes();
		boolean succeeded = false;
		while(!succeeded) {
			ArrayList<BeliefNode> setEvidences = new ArrayList<BeliefNode>(); // remember nodes for which we set evidences while sampling
			for(int i = 0; i < order.length; i++) {
				BeliefNode node = nodes[order[i]];
				if(node.hasEvidence()) {
					throw new Exception("TODO evidence case unhandled");
				}
				int idxValue = ForwardSampling.sampleForward(node, bn, generator);			
				if(idxValue == -1) {
					// sampling node failed - most probably because the distribution was all 0 values -> retry from start
					succeeded = false;
					break;
				}
				succeeded = true;
				Domain dom = node.getDomain();
				//System.out.println("set node " + node.getName() + " to " + dom.getName(idxValue));
				ret.put(node.getName(), dom.getName(idxValue));
				node.setEvidence(new DiscreteEvidence(idxValue));
				setEvidences.add(node);				
			}
			// remove evidences (restoring original state)
			for(BeliefNode node : setEvidences) {
				node.setEvidence(null);
			}
		}
		return ret;
	}
}
