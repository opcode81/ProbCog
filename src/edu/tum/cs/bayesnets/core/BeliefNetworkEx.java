package edu.tum.cs.bayesnets.core;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import edu.ksu.cis.bnj.ver3.core.*;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.ksu.cis.bnj.ver3.inference.approximate.sampling.ForwardSampling;
import edu.ksu.cis.bnj.ver3.inference.exact.Pearl;
import edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader;
import edu.ksu.cis.bnj.ver3.streams.*;
import edu.ksu.cis.util.graph.algorithms.TopologicalSort;
import edu.tum.cs.bayesnets.core.io.Converter_pmml;
import edu.tum.cs.bayesnets.core.io.Converter_xmlbif;

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
	 * The logger for this class.
	 */
	static final Logger logger = Logger.getLogger(BeliefNetworkEx.class);
	static {
		logger.setLevel(Level.WARN);
	}

	/**
	 * The maximum number of unsuccessful trials for sampling. 
	 * TODO: This should perhaps depend on the number of samples to be gathered?
	 */
	public static final int MAX_TRIALS = 5000;

	/**
	 * An instance of class <code>WeightedSample</code> represents a weighted
	 * sample. It contains the mapping from node to the corresponding value and
	 * the value of the sample.
	 * 
	 * @see BeliefNetworkEx#getWeightedSample(String[][], Random)
	 */
	public class WeightedSample {
		/**
		 * The mapping from intern numbering to value as index into the domain
		 * of the node.
		 */
		public int[] nodeDomainIndices;
		/**
		 * The mapping from intern numbering to the node index of the outer
		 * class {@link BeliefNetworkEx}.
		 */
		public int[] nodeIndices;
		/**
		 * The weight of the sample.
		 */
		public double weight;

		/**
		 * Constructs a weighted sample from given node value mapping and
		 * weight.
		 * 
		 * @param nodeDomainIndices
		 *            the mapping from intern numbering to value as index into
		 *            the domain of the node.
		 * @param weight
		 *            the weight of the sample.
		 * @param nodeIndices
		 *            the mapping from intern numbering to the node index of the
		 *            outer class.
		 */
		public WeightedSample(int[] nodeDomainIndices, double weight,
				int[] nodeIndices) {
			if (nodeIndices == null) {
				int numNodes = nodeDomainIndices.length;
				nodeIndices = new int[numNodes];
				for (int i = 0; i < numNodes; i++) {
					nodeIndices[i] = i;
				}
			}
			this.nodeIndices = nodeIndices;
			this.nodeDomainIndices = nodeDomainIndices;
			assert nodeIndices.length == nodeDomainIndices.length;
			this.weight = weight;
		}

		/**
		 * Extract a sub sample of this sample for the given nodes. The weight
		 * of the sample has to be normalised afterwards and is only meaningful
		 * with the same node base!
		 * 
		 * @param queryNodes
		 *            the nodes to be extracted.
		 * @return the sub sample for the given nodes.
		 */
		public WeightedSample subSample(int[] queryNodes) {
			logger.debug(Arrays.toString(nodeDomainIndices));
			int[] resultIndices = new int[queryNodes.length];
			for (int i = 0; i < queryNodes.length; i++) {
				resultIndices[i] = nodeDomainIndices[queryNodes[i]];
			}
			return new WeightedSample(resultIndices, weight, queryNodes);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Arrays.hashCode(nodeDomainIndices);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof WeightedSample))
				return false;
			return Arrays.equals(nodeDomainIndices,
					(((WeightedSample) obj).nodeDomainIndices));
		}

		/**
		 * Get the assignment from node names to the values. Sometimes we don't
		 * want to use the internal numbering outside this class.
		 * 
		 * @return the assignments of this sample.
		 */
		public Map<String, String> getAssignmentMap() {
			Map<String, String> result = new HashMap<String, String>();

			BeliefNode[] nodes = BeliefNetworkEx.this.bn.getNodes();
			for (int i = 0; i < nodeIndices.length; i++) {
				try {
					result.put(nodes[nodeIndices[i]].getName(),
							nodes[nodeIndices[i]].getDomain().getName(
									nodeDomainIndices[i]));
				} catch (RuntimeException e) {
					e.printStackTrace();
					throw e;
				}
			}

			return result;
		}

		/**
		 * Get the assignment from node names to values but use an example value
		 * for {@link Discretized} domains.
		 * 
		 * @return the assignments of this sample probably with example values.
		 */
		public Map<String, String> getUndiscretizedAssignmentMap() {
			Map<String, String> result = new HashMap<String, String>();

			BeliefNode[] nodes = BeliefNetworkEx.this.bn.getNodes();
			for (int i = 0; i < nodeIndices.length; i++) {
				try {
					Domain nodeDomain = nodes[nodeIndices[i]].getDomain();
					String value = nodeDomain.getName(nodeDomainIndices[i]);
					if (nodeDomain instanceof Discretized) {
						value = String.valueOf(((Discretized) nodeDomain)
								.getExampleValue(nodeDomainIndices[i]));
					}
					result.put(nodes[nodeIndices[i]].getName(), value);
				} catch (RuntimeException e) {
					e.printStackTrace();
					throw e;
				}
			}
			return result;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "WeightedSample(" + getAssignmentMap() + ", " + weight + ")";
		}

		/**
		 * Get only a shorter String containing only the the domain indices
		 * instead of the full assignment map.
		 * 
		 * @return a short string representation.
		 */
		public String toShortString() {
			return "WeightedSample(" + Arrays.toString(nodeDomainIndices)
					+ ", " + weight + ")";
		}

		/**
		 * Check if all query assignments are this sample's assignments.
		 * 
		 * @param queries
		 *            the assignments to be tested.
		 * @return  true if all the assignments are correct,
		 * 	       false otherwise
		 */
		public boolean checkAssignment(String[][] queries) {
			int[] indices = getNodeDomainIndicesFromStrings(queries);
			for (int nodeIndex : nodeIndices) {
				if (indices[nodeIndex] >= 0
						&& indices[nodeIndex] != nodeDomainIndices[nodeIndex])
					return false;
			}
			return true;
		}
	}
	
	/**
	 * class that allows the incremental construction of a probability distribution from (weighted) samples
	 * (see {@link WeightedSample})
	 * @author jain
	 *
	 */
	public static class SampledDistribution {
		protected double[][] sums;
		protected double Z;
		protected BeliefNetworkEx bn;
		
		public SampledDistribution(BeliefNetworkEx bn) {
			this.bn = bn;
			BeliefNode[] nodes = bn.bn.getNodes();
			sums = new double[nodes.length][];
			for(int i = 0; i < nodes.length; i++)
				sums[i] = new double[nodes[i].getDomain().getOrder()];			
		}
		
		public void addSample(WeightedSample s) {
			Z += s.weight;
			for(int i = 0; i < s.nodeIndices.length; i++) {
				sums[s.nodeIndices[i]][s.nodeDomainIndices[i]] += s.weight;
			}			
		}
		
		public void print(PrintStream out) {			
			for(int i = 0; i < bn.bn.getNodes().length; i++) {
				printNodeDistribution(out, i);
			}
		}
		
		public void printNodeDistribution(PrintStream out, int index) {
			BeliefNode node = bn.bn.getNodes()[index];
			out.println(node.getName() + ":");
			Discrete domain = (Discrete)node.getDomain();
			for(int j = 0; j < domain.getOrder(); j++) {
				double prob = sums[index][j] / Z;
				out.println(String.format("  %.4f %s", prob, domain.getName(j)));
			}
		}
	}
	
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
	 * the name of the currently loaded belief network file
	 */
	protected String filename;
	
	/**
	 * The mapping from attribute names to the node names of nodes that should get data from the attribute.
	 */
	protected Map<String, String> nodeNameToAttributeMapping = new HashMap<String, String>();
	
	/**
	 * The inverse mapping of {@link #nodeNameToAttributeMapping}.
	 */
	protected Map<String, Set<String>> attributeToNodeNameMapping = new HashMap<String, Set<String>>();
	
	/**
	 * constructs a BeliefNetworkEx object from a BNJ BeliefNetwork object
	 * @param bn	the BNJ BeliefNetwork object
	 */
	public BeliefNetworkEx(BeliefNetwork bn) {
		this.bn = bn;
		initAttributeMapping();
	}
	
	/**
	 * constructs a BeliefNetworkEx object from a saved XML-BIF file
	 * @param xmlbifFile	the name of the XML-BIF file to load the network from
	 */
	public BeliefNetworkEx(String xmlbifFile) throws FileNotFoundException {
		this.bn = load(xmlbifFile, new Converter_xmlbif());
		initAttributeMapping();
		this.filename = xmlbifFile;
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
		initAttributeMapping();
		this.filename = filename;
	}
	
	/**
	 * constructs an empty network. Use methods addNode and connect to define the network structure.  
	 */ 
	public BeliefNetworkEx() {
		this.bn = new BeliefNetwork();
	}
	
	/**
	 * Initialize the attribute mapping with the basenodes' names to itself respectively.
	 */
	protected void initAttributeMapping() {
	    for (BeliefNode node: bn.getNodes()) {
			addAttributeMapping(node.getName(), node.getName());
	    }
	}
	
	/**
	 * Add a link from the node name to the attribute name.
	 * Insert an entry into {@link #nodeNameToAttributeMapping} and into {@link #attributeToNodeNameMapping}.
	 * @param nodeName 	the name of the node to link.
	 * @param attributeName	the name of the attribute to be linked with the node.
	 */
	protected void addAttributeMapping(String nodeName, String attributeName) {
	    nodeNameToAttributeMapping.put(nodeName, attributeName);
	    Set<String> nodeNames = attributeToNodeNameMapping.get(attributeName);
	    if (nodeNames == null) {
	    	nodeNames = new HashSet<String>();
	    	attributeToNodeNameMapping.put(attributeName, nodeNames);
	    }
	    nodeNames.add(nodeName);
	}
	
	/**
	 * Get the attribute name that is linked to the given node.
	 * @param nodeName	the name of the node.
	 * @return		the attribute's name. 
	 */
	public String getAttributeNameForNode(String nodeName) {
	    return nodeNameToAttributeMapping.get(nodeName);
	}
	
	/**
	 * Get the node names that are linked to the given attribute name.
	 * @param attributeName	the attribute name the nodes are linked to.
	 * @return				the node names that are linked to the attribute.
	 */
	public Set<String> getNodeNamesForAttribute(String attributeName) {
		return attributeToNodeNameMapping.get(attributeName);
	}
	
	/**
	 * adds a node to the network
	 * @param node	the node that is to be added
	 */
	public void addNode(BeliefNode node) {
		bn.addBeliefNode(node);
		addAttributeMapping(node.getName(), node.getName());
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
	 * adds a node with the given name and domain to the network.
	 * Associate the attribute with the same name to the node.
	 * @param name		the name of the node
	 * @param domain	the node's domain (usually an instance of BNJ's class Discrete)
	 * @return			a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name, Domain domain) {
		return addNode(name, domain, name);
	}
	
	/**
	 * adds a node with the given name and domain and attribute name to the network.
	 * @param name		the name of the node
	 * @param domain	the node's domain (usually an instance of BNJ's class Discrete)
	 * @param attributeName	the name of the attribute that is assigned to the node
	 * @return			a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name, Domain domain, String attributeName) {
		BeliefNode node = new BeliefNode(name, domain);
		bn.addBeliefNode(node);
		addAttributeMapping(name, attributeName);
		logger.debug("Added node "+name+" with attributeName "+attributeName);
		return node;
	}
	
	/**
	 * adds an edge to the network, i.e. a dependency
	 * @param node1		the name of the node that influences another
	 * @param node2		the name of node that is influenced
	 * @throws Exception	if either of the node names are invalid
	 */
	public void connect(String node1, String node2) throws Exception {
		try {
			logger.debug("connecting "+node1+" and "+node2);
			logger.debug("Memory free: "+Runtime.getRuntime().freeMemory()+"/"+Runtime.getRuntime().totalMemory());
			BeliefNode n1 = getNode(node1);
			BeliefNode n2 = getNode(node2);
			if(n1 == null || n2 == null)
				throw new Exception("One of the node names "+node1+" or "+node2+" is invalid!");
			logger.debug("Domainsize: "+n1.getDomain().getOrder()+"x"+n2.getDomain().getOrder());
			logger.debug("Doing the connect...");
			bn.connect(n1, n2);
			logger.debug("Memory free: "+Runtime.getRuntime().freeMemory()+"/"+Runtime.getRuntime().totalMemory());
			logger.debug("Connection done.");
		} catch(Exception e) {
			System.out.println("Exception occurred in connect!");
			e.printStackTrace(System.out);
			throw e;
		} catch(Error e2) {
			System.out.println("Error occurred");
			e2.printStackTrace(System.out);
			throw e2;
		}
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
	 * Get the indices of the nodes that the CPT of the given node depends on.
	 * @param node	the node to take the CPT from. 
	 * @return		the indices of the nodes that the CPT of the given node depends on.
	 */
	public int[] getDomainProductNodeIndices(BeliefNode node) {
		BeliefNode[] nodes = node.getCPF().getDomainProduct();
		int[] nodeIndices = new int[nodes.length];
		for(int i = 0; i < nodes.length; i++)
			nodeIndices[i] = this.getNodeIndex(nodes[i].getName());
		return nodeIndices;
	}
	
	/**
	 * Get the indices into the domains of the nodes for the given node value assignments.
	 * @param nodeAndDomains	the assignments to be converted.
	 * @return					the assignment converted to doamin indices.
	 */
	public int[] getNodeDomainIndicesFromStrings(String[][] nodeAndDomains) {
		BeliefNode[] nodes = bn.getNodes(); 
		int[] nodeDomainIndices = new int[nodes.length];
		Arrays.fill(nodeDomainIndices, -1);
		for (String[] nodeAndDomain: nodeAndDomains) {
			if (nodeAndDomain == null || nodeAndDomain.length != 2)
				throw new IllegalArgumentException("Evidences not in the correct format: "+Arrays.toString(nodeAndDomain)+"!");
			int nodeIdx = getNodeIndex(nodeAndDomain[0]);
			if (nodeIdx < 0)
				throw new IllegalArgumentException("Variable with the name "+nodeAndDomain[0]+" not found!");
			if (nodeDomainIndices[nodeIdx] > 0)
				logger.warn("Evidence "+nodeAndDomain[0]+" set twice!");
			Discrete domain = (Discrete)nodes[nodeIdx].getDomain();
			int domainIdx = domain.findName(nodeAndDomain[1]);
			if (domainIdx < 0) {
				if (domain instanceof Discretized) {
					try {
						double value = Double.parseDouble(nodeAndDomain[1]);
						String domainStr = ((Discretized)domain).getNameFromContinuous(value);
						domainIdx = domain.findName(domainStr);
					} catch (Exception e) {
						throw new IllegalArgumentException("Cannot find evidence value "+nodeAndDomain[1]+" in domain "+domain+"!");
					}
				} else {
					throw new IllegalArgumentException("Cannot find evidence value "+nodeAndDomain[1]+" in domain "+domain+"!");
				}
			}
			nodeDomainIndices[nodeIdx]=domainIdx;
		}
		return nodeDomainIndices;
	}
	
	public int getNodeIndex(BeliefNode node) {
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++)
			if(nodes[i] == node)
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
	 * writes the Bayesian network to the same file it was loaded from
	 * @throws Exception 
	 *
	 */
	public void save() throws Exception {
		IOPlugInLoader pil = IOPlugInLoader.getInstance();
		if(filename == null)
			throw new Exception("Cannot save - filename not given!");
		Exporter exporter = pil.GetExportersByExt(pil.GetExt(filename));
		save(filename, exporter);
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
		window.open(bn, filename);
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
	 * Get the sample assignment and its sampled probability as the weight sorted by probability.
	 * @param evidences		the evidences for the distribution.
	 * @param queryNodes	the nodes that should be sampled.
	 * @param numSamples	the number of samples to draw from.
	 * @return				the accumulated samples and their sampled conditional probabilities given the evidences 
	 * 						or null	if we run out of trials for the first sample. 
	 */
	public WeightedSample[] getAssignmentDistribution(String[][] evidences, String[] queryNodeNames, int numSamples) {
		HashMap<WeightedSample, Double> sampleSums = new HashMap<WeightedSample, Double>();

		int[] queryNodes = new int[queryNodeNames.length];
		for (int i=0; i<queryNodeNames.length; i++) {
			queryNodes[i]=getNodeIndex(queryNodeNames[i]);
			if (queryNodes[i] < 0)
				throw new IllegalArgumentException("Cannot find node with name "+queryNodeNames[i]);
		}

		Random generator = new Random();
		for (int i=0; i<numSamples; i++) {
			WeightedSample sample = getWeightedSample(evidences, generator);
			if (sample == null && i == 0)	// If we need too many trials and we have no samples 
				return null;				// it will be very probable that we have an endless loop because of a bad evidence!
			WeightedSample subSample = sample.subSample(queryNodes);

			if (sampleSums.containsKey(subSample)) {
				sampleSums.put(subSample, sampleSums.get(subSample)+subSample.weight);
			} else {
				sampleSums.put(subSample, subSample.weight);
			}
		}
		double sum = 0;
		for (WeightedSample sample: sampleSums.keySet()) {
			logger.debug(sample);
			double value = sampleSums.get(sample);
			sum += value;
		}
		WeightedSample[] samples = sampleSums.keySet().toArray(new WeightedSample[0]);
		for (WeightedSample sample: samples) {
			sample.weight = sampleSums.get(sample)/sum;
		}
		
		Arrays.sort(samples, new Comparator<WeightedSample>() {
			public int compare(WeightedSample o1, WeightedSample o2) {
				return Double.compare(o2.weight, o1.weight);
			}
		});

		return samples;
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
	 * Get a specific entry in the cpt of the given node.
	 * The nodeDomainIndices should contain a value for each node in the BeliefNet but only values
	 * in the domain product of the node are queried for.
	 * @param node				the node the CPT should come from.
	 * @param nodeDomainIndices	the values the nodes should have.
	 * @return					the probability entry in the CPT.
	 */
	public double getCPTProbability(BeliefNode node, int[] nodeDomainIndices ) {
		CPF cpf = node.getCPF();
		int[] domainProduct = getDomainProductNodeIndices(node);
		int[] address = new int[domainProduct.length];
		for (int i=0; i<address.length; i++) {
			address[i]=nodeDomainIndices[domainProduct[i]];
		}
		int realAddress = cpf.addr2realaddr(address);
		return ((ValueDouble)cpf.get(realAddress)).getValue();
	}
	
	/**
	 * Remove all evidences.
	 */
	public void removeAllEvidences() {
		// remove evidences (restoring original state)
		for(BeliefNode node : bn.getNodes()) {
			node.setEvidence(null);
		}
	}
	
	/**
	 * Calculates a probability Pr[X=x, Y=y, ... | E=e, F=f, ...] by sampling a number of samples.
	 * @param queries		an array of 2-element string arrays (variable, outcome)
	 * 						that represents the conjunction "X=x AND Y=y AND ...".
	 * @param evidences		the conjunction of evidences, specified in the same way.
	 * @param numSamples	the number of samples to draw.
	 * @return				the calculated probability.
	 */
	public double getSampledProbability(String[][] queries, String[][] evidences, int numSamples) {
	    String[] queryNodes = new String[queries.length];
	    for (int i=0; i<queryNodes.length; i++) {
	    	queryNodes[i]=queries[i][0];
	    }
	    WeightedSample[] samples = getAssignmentDistribution(evidences, queryNodes, numSamples);
	    double goodSum = 0;
	    double allSum = 0;
	    for (int i=0; i<samples.length; i++) {
			allSum += samples[i].weight;
			if (samples[i].checkAssignment(queries))
			    goodSum += samples[i].weight;
	    }
	    return goodSum/allSum;
	}
	
	/**
	 * Sample from the BeliefNet via likelihood weighted sampling.
	 * @param evidences				the evidences for the sample.
	 * @param sampleDomainIndexes	the resulting domain indexes for each node.
	 * 			The length must be initialized to the number of nodes in the net.
	 * @return
	 */
	public WeightedSample getWeightedSample(String[][] evidences, Random generator) {		
		if (generator == null) {
			generator = new Random();
		}		
		return getWeightedSample(getTopologicalOrder(), evidence2DomainIndices(evidences), generator);
	}
	
	public WeightedSample getWeightedSample(int[] nodeOrder, int[] evidenceDomainIndices, Random generator) {
		BeliefNode[] nodes = bn.getNodes();
		int[] sampleDomainIndices  = new int[nodes.length];
		boolean successful = false;
		double weight = 1.0;
		int trials=0;
success:while (!successful) {
			weight = 1.0;
			if (trials > MAX_TRIALS)
				return null;
			for (int i=0; i< nodeOrder.length; i++) {
				int nodeIdx = nodeOrder[i];
				int domainIdx = evidenceDomainIndices[nodeIdx];
				if (domainIdx >= 0) { // This is an evidence node?
					sampleDomainIndices[nodeIdx] = domainIdx;
					nodes[nodeIdx].setEvidence(new DiscreteEvidence(domainIdx));
					double prob = getCPTProbability(nodes[nodeIdx], sampleDomainIndices);
					if (prob == 0.0) {		
						removeAllEvidences();
						trials++;
						continue success;
					}
					weight *= prob;
				} else {
					domainIdx = ForwardSampling.sampleForward(nodes[nodeIdx], bn, generator);
					if (domainIdx < 0) {
						removeAllEvidences();
						trials++;
						continue success;
					}
					sampleDomainIndices[nodeIdx] = domainIdx;
					nodes[nodeIdx].setEvidence(new DiscreteEvidence(domainIdx));
				}
			}
			trials++;
			removeAllEvidences();
			successful = true;
		}
		return new WeightedSample(sampleDomainIndices, weight, null);		
	}
	
	public int[] evidence2DomainIndices(String[][] evidences) {
		BeliefNode[] nodes = bn.getNodes();
		int[] evidenceDomainIndices = new int[nodes.length];
		Arrays.fill(evidenceDomainIndices, -1);
		for (String[] evidence: evidences) {
			if (evidence == null || evidence.length != 2)
				throw new IllegalArgumentException("Evidences not in the correct format: "+Arrays.toString(evidence)+"!");
			int nodeIdx = getNodeIndex(evidence[0]);
			if (nodeIdx < 0)
				throw new IllegalArgumentException("Variable with the name "+evidence[0]+" not found!");
			if (evidenceDomainIndices[nodeIdx] > 0)
				logger.warn("Evidence "+evidence[0]+" set twice!");
			Discrete domain = (Discrete)nodes[nodeIdx].getDomain();
			int domainIdx = domain.findName(evidence[1]);
			if (domainIdx < 0) {
				if (domain instanceof Discretized) {
					try {
						double value = Double.parseDouble(evidence[1]);
						String domainStr = ((Discretized)domain).getNameFromContinuous(value);
						domainIdx = domain.findName(domainStr);
					} catch (Exception e) {
						throw new IllegalArgumentException("Cannot find evidence value "+evidence[1]+" in domain "+domain+"!");
					}
				} else {
					throw new IllegalArgumentException("Cannot find evidence value "+evidence[1]+" in domain "+domain+"!");
				}
			}
			evidenceDomainIndices[nodeIdx]=domainIdx;
		}
		return evidenceDomainIndices;
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
					throw new Exception("At least one node has evidence. You can only sample from the marginal distribution!");
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
	
	public static String[] getDiscreteDomainAsArray(BeliefNode node) {
		Discrete domain = (Discrete)node.getDomain();
		String[] ret = new String[domain.getOrder()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = domain.getName(i);
		return ret;		
	}
	
	public String[] getDiscreteDomainAsArray(String nodeName) {
		return getDiscreteDomainAsArray(getNode(nodeName));
	}
	
	/**
	 * Dump the context of the net to the logger.
	 */
	public void dump() {
		BeliefNode[] nodes = bn.getNodes();
		for (int i=0; i<nodes.length; i++) {
			logger.debug("Node "+i+": "+nodes[i].getName());
			logger.debug("\tAttribute: "+getAttributeNameForNode(nodes[i].getName()));
		}
		for (String attributeName: attributeToNodeNameMapping.keySet()) {
			logger.debug("Attribute "+attributeName+": "+attributeToNodeNameMapping.get(attributeName));
		}
	}

	public abstract class CPTWalker {
		public abstract void tellSize(int childConfigs, int parentConfigs);
		public abstract void tellNodeOrder(BeliefNode n);		
		public abstract void tellValue(double v);
	}
	
	public void walkCPT(BeliefNode node, CPTWalker walker) {
		CPF cpf = node.getCPF();
		BeliefNode[] nodes = cpf.getDomainProduct();
		int parentConfigs = 1;
		for(int i = 1; i < nodes.length; i++)
			parentConfigs *= nodes[i].getDomain().getOrder();
		walker.tellSize(nodes[0].getDomain().getOrder(), parentConfigs);
		int[] addr = new int[cpf.getDomainProduct().length];
		walkCPT(walker, cpf, addr, 0);
	}
	
	protected void walkCPT(CPTWalker walker, CPF cpf, int[] addr, int i) {
		BeliefNode[] nodes = cpf.getDomainProduct();
		if(i == addr.length) { // we have a complete address
			// get the probability value
			int realAddr = cpf.addr2realaddr(addr);
			double value = ((ValueDouble)cpf.get(realAddr)).getValue();
			walker.tellValue(value);
		}
		else { // the address is yet incomplete -> consider all ways of setting the next e
			walker.tellNodeOrder(nodes[i]);
			Discrete dom = (Discrete)nodes[i].getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				walkCPT(walker, cpf, addr, i+1);
			}
		}
	}
	
	/**
	 * gets the index of the given value inside the given node's domain
	 * @param node  a node with a discrete domain
	 * @param value  the value whose index to search for
	 * @return  the index of the value in the node's domain
	 */
	public int getDomainIndex(BeliefNode node, String value) {
		Discrete domain = (Discrete)node.getDomain();
		return domain.findName(value);
	}
}
