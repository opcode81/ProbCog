package edu.tum.cs.bayesnets.learning;

import de.tum.in.fipm.base.data.QueryResult;
import edu.ksu.cis.bnj.ver3.core.*;
import edu.ksu.cis.bnj.ver3.core.values.Field;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import java.sql.*; 
import java.util.*;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

import weka.clusterers.*;
import weka.core.*;

/**
 * learns the conditional probability tables for all nodes in a Bayesian network 
 * when given a set of examples. CPTs are learnt by initializing all the table values to zero
 * and incrementing individual values whenever a corresponding example is passed.
 * In the end, probablities are obtained by means of normalization.  
 * @author Dominik Jain
 */
public class CPTLearner extends Learner {
	
	/**
	 * an array of example counter objects - one for each node in the network 
	 */
	protected ExampleCounter[] counters;
	/**
	 * an array of clusterers - one for each node;
	 * for nodes that do not use clustering to determine the index of the domain, the entry is null
	 */
	protected Clusterer[] clusterers;
	
	/**
	 * constructs a CPTLearner object from a BeliefNetworkEx object
	 * @param bn
	 */
	public CPTLearner(BeliefNetworkEx bn) {
		super(bn);
		init();
	}
	
	/**
	 * constructs a CPTLearner object from a DomainLearner. If you consecutively want to
	 * learn domains and CPTs, you should make use of this constructor, because it relieves
	 * you of the burden of having to pass the clusterers that categorize instances for
	 * certain domains manually (duplicate domains are taken into consideration, i.e. clusterers
	 * will be reused appropriately).
	 * @param dl			the domain learner
	 * @throws Exception
	 */
	public CPTLearner(DomainLearner dl) throws Exception {
		super(dl.bn.bn);
		init();
		// initialize clusterers from the domain learner
		if(dl.clusteredDomains != null) {	
			for(int i = 0; i < dl.clusteredDomains.length; i++)
				addClusterer(dl.clusteredDomains[i].nodeName, dl.clusterers[i]);
			if(dl.duplicateDomains != null) {
				for(int i = 0; i < dl.duplicateDomains.length; i++)
					for(int j = 0; j < dl.clusteredDomains.length; j++)
						if(dl.duplicateDomains[i][0].equals(dl.clusteredDomains[j].nodeName)) {
							for(int k = 1; k < dl.duplicateDomains[i].length; k++)
								addClusterer(dl.duplicateDomains[i][k], dl.clusterers[j]);					
							break;
						}
			}
		}
	}
	
	/**
	 * initializes the array of clusterers (initially an array of null references)
	 * and the array of example counters (one for each node) 
	 */
	private void init() {
		clusterers = new Clusterer[nodes.length];
        // create example counters for each node
        counters = new ExampleCounter[nodes.length];		
        for(int i = 0; i < nodes.length; i++)
        	counters[i] = new ExampleCounter(nodes[i], bn);
	}
	
	/**
	 * learns all the examples in the result set. Each row in the result set represents one example.
	 * All the random variables (nodes) in the network
	 * need to be found in each result row as columns that are named accordingly, i.e. for each
	 * random variable, there must be a column with a matching name in the result set. 
	 * @param rs			the result set
	 * @throws Exception 	if the result set is empty
	 * @throws SQLException particularly if there is no matching column for one of the node names  
	 */
	public void learn(ResultSet rs) throws Exception {
        try {
			// if it's an empty result set, throw exception
			if(!rs.next())
				throw new Exception("empty result set!");

			BeliefNode[] nodes = bn.bn.getNodes();
			ResultSetMetaData rsmd = rs.getMetaData();
			int numCols = rsmd.getColumnCount();
			if(numCols != nodes.length)
				throw new Exception("Result does not contain suitable data (column count = " + numCols + "; node count = " + nodes.length + ")");
			
			// map node indices to result set column indices
			int[] nodeIdx2colIdx = new int[nodes.length];
			for(int i = 1; i <= numCols; i++) {				
				int node_idx = bn.getNodeIndex(rsmd.getColumnName(i));
				if(node_idx == -1)
					throw new Exception("Unknown node referenced in result set: " + rsmd.getColumnName(i));
				nodeIdx2colIdx[node_idx] = i;
			}			
			
            // gather data, iterating over the result set
			int[] domainIndices = new int[nodes.length];
            do {
				// for each row...
				// - get the indices into the domains of each node
				//   that correspond to the current row of data
				//   (sorted in the same order as the nodes are ordered
				//   in the BeliefNetwork)				
				for(int node_idx = 0; node_idx < nodes.length; node_idx++) {
					int domain_idx;
					if(clusterers[node_idx] == null) {
						Discrete domain = (Discrete) nodes[node_idx].getDomain();
						String value = rs.getString(nodeIdx2colIdx[node_idx]);
						domain_idx = domain.findName(value);
						if(domain_idx == -1)
							throw new Exception(value + " not found in domain of " + nodes[node_idx].getName());				
					}
					else {
						Instance inst = new Instance(1);
						inst.setValue(0, rs.getDouble(nodeIdx2colIdx[node_idx]));
						domain_idx = clusterers[node_idx].clusterInstance(inst);
					}
					domainIndices[node_idx] = domain_idx;
				}
            	// - update each node's CPT
            	for(int i = 0; i < nodes.length; i++) {
            		counters[i].count(domainIndices);
            	}
            } while(rs.next());
        } 
        catch (SQLException ex) { // handle any database errors             
            System.out.println("SQLException: " + ex.getMessage()); 
            System.out.println("SQLState: " + ex.getSQLState()); 
            System.out.println("VendorError: " + ex.getErrorCode()); 
        }
	}
	
	/**
	 * learns an example from a HashMap&lt;String,String&gt;. 
	 * @param data			a HashMap containing the data for one example. The names of all the random 
	 * 						variables (nodes) in the network must be found in the set of keys of the 
	 * 						hash map. 
	 * @throws Exception	if required keys are missing from the HashMap
	 */
	public void learn(Map<String,String> data) throws Exception {					
		// - get the indices into the domains of each node
		//   that correspond to the current row of data
		//   (sorted in the same order as the nodes are ordered
		//   in the BeliefNetwork)				
		BeliefNode[] nodes = bn.bn.getNodes();
		int[] domainIndices = new int[nodes.length];
		for(int node_idx = 0; node_idx < nodes.length; node_idx++) {
			int domain_idx;
			String value = data.get(nodes[node_idx].getName());
			if(value == null)
				throw new Exception("Key " + nodes[node_idx].getName() + " not found in data!");
			if(clusterers[node_idx] == null) {
				Discrete domain = (Discrete) nodes[node_idx].getDomain();
				domain_idx = domain.findName(value);
				if(domain_idx == -1)
					throw new Exception(value + " not found in domain of " + nodes[node_idx].getName());				
			}
			else {
				Instance inst = new Instance(1);
				inst.setValue(0, Double.parseDouble(value));
				domain_idx = clusterers[node_idx].clusterInstance(inst);
			}
			domainIndices[node_idx] = domain_idx;
		}
    	// - update each node's CPT
    	for(int i = 0; i < nodes.length; i++) {
    		counters[i].count(domainIndices);
    	}
	}
	
	/**
	 * learns all the examples in a fipm.data.QueryResult (otherwise analogous to learn(ResultSet))
	 * @param res			the query result containing the data for a set of examples
	 * @throws Exception
	 */
	public void learn(QueryResult res) throws Exception {
		// map node indices to result set column indices
		Vector colnames = res.getColumnNames();
		int[] nodeIdx2colIdx = new int[nodes.length];
		for(int i = 0; i < nodes.length; i++) {
			nodeIdx2colIdx[i] = colnames.indexOf(nodes[i].getName());
			if(nodeIdx2colIdx[i] == -1)
				throw new Exception("Incomplete result set; missing: " + nodes[i].getName());			
		}			
		
        // gather data, iterating over the result set
		int[] domainIndices = new int[nodes.length];
		for(int k = 0; k < res.getRowCount(); k++) {
			// for each row...
			Vector row = res.getRow(k);
			// - get the indices into the domains of each node
			//   that correspond to the current row of data
			//   (sorted in the same order as the nodes are ordered
			//   in the BeliefNetwork)			
			for(int node_idx = 0; node_idx < nodes.length; node_idx++) {
				int domain_idx;
				if(clusterers[node_idx] == null) {
					Discrete domain = (Discrete) nodes[node_idx].getDomain();
					String value = (String) row.get(nodeIdx2colIdx[node_idx]);
					domain_idx = domain.findName(value);
					if(domain_idx == -1)
						throw new Exception(value + " not found in domain of " + nodes[node_idx].getName());				
				}
				else {
					Instance inst = new Instance(1);
					inst.setValue(0, Double.parseDouble((String)row.get(nodeIdx2colIdx[node_idx])));
					domain_idx = clusterers[node_idx].clusterInstance(inst);
				}
				domainIndices[node_idx] = domain_idx;
			}
        	// - update each node's CPT
        	for(int i = 0; i < nodes.length; i++) {
        		counters[i].count(domainIndices);
        	}
        } 
		
	}
	
	/**
	 * tells the CPTLearner to use a clusterer to categorize instances (i.e. example outcomes)
	 * for a certain node.
	 * @param nodeName		the name of the node
	 * @param clusterer		the clusterer to use for categorization
	 * @throws Exception	if the name of the node is invalid
	 */
	public void addClusterer(String nodeName, Clusterer clusterer) throws Exception {
		for(int i = 0; i < nodes.length; i++)
			if(nodes[i].getName().equals(nodeName)) {
				clusterers[i] = clusterer;
				return;
			}
		throw new Exception("Passed unknown node name!");
	}
	
	/**
	 * normalizes the CPTs (is called by finish and should not be called)
	 */
	protected void end_learning() {
        // normalize the CPTs
        for(int i = 0; i < nodes.length; i++)
        	nodes[i].getCPF().normalizeByDomain();
	}

	
	/**
	 * 	An instance of this class counts examples for a given node.
	 */
	protected class ExampleCounter {
		CPF cpf;
		/** 
		 * indices of relevant nodes (parents and node itself)
		 */
		public int[] nodeIndices;

		/**
		 * creates an ExampleCounter object for one of the nodes in a Bayesian network
		 * @param n		the node
		 * @param bn	the Bayesian Network the node is part of
		 */
		public ExampleCounter(BeliefNode n, BeliefNetworkEx bn) {
			// empty the cpf (initialize values to 0)
			cpf = n.getCPF();			
			for(int i = 0; i < cpf.size(); i++)
				cpf.put(i, new ValueDouble(0));
			
			// get the indices of the nodes that the CPT depends on
			BeliefNode[] nodes = cpf.getDomainProduct();
			nodeIndices = new int[nodes.length];
			for(int i = 0; i < nodes.length; i++)
				nodeIndices[i] = bn.getNodeIndex(nodes[i].getName());	
		}
	
		/**
		 * increments the value in the CPT that corresponds to the example
		 * @param domainIndices		a complete example (i.e. an example containing
		 * 							values for each node) specified as an array of integers, 
		 * 							where each value is an index into the corresponding node's 
		 * 							domain, the order being determined by the BeliefNetwork's 
		 * 							array of nodes as returned by getNodes().
		 */
		public void count(int[] domainIndices) {

			int[] addr = new int[nodeIndices.length];
			
			// get the address of the CPT field
			for(int i = 0; i < nodeIndices.length; i++) {
				addr[i] = domainIndices[nodeIndices[i]];
			}
			
			// get the real address of the table entry
			int realAddr = cpf.addr2realaddr(addr);
			// add one to the entry
			cpf.put(realAddr, Field.add(cpf.get(realAddr), new ValueDouble(1)) );
		}
	}
}
