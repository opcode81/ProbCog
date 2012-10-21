/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
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
package probcog.bayesnets.learning;

import edu.ksu.cis.bnj.ver3.core.*;

import java.sql.*;
import java.util.*;

import probcog.bayesnets.core.*;
import probcog.clustering.ClusterNamer;

import weka.core.*;
import weka.clusterers.*;
//import de.tum.in.fipm.base.data.QueryResult;

/**
 * learns domains for a certain set of nodes in a Bayesian network when given a
 * set of examples to learn from. The domains of discrete variables can be
 * learnt directly (i.e. the set of outcomes found in the examples becomes the
 * new domain); the domains of continuous variables can be learnt using
 * clustering. For clustering, WEKA's SimpleKMeans clustering algorithm is used,
 * which yields a Gaussian distribution for each cluster (i.e. expected value
 * (centroid) and standard deviation).
 * 
 * @author Dominik Jain
 */
public class DomainLearner extends Learner {

	/**
	 * an array of ClusteredDomain objects, where each object contains
	 * information on a node whose domain is to be learnt via clustering
	 */
	ClusteredDomain[] clusteredDomains;

	/**
	 * an array of instance collections for clustering; one collection is to
	 * hold all the instances that were encountered for one of the nodes whose
	 * domains are learnt via clustering. Each entry in the array corresponds to
	 * an entry in clusteredDomains.
	 */
	protected Instances[] clusterData;

	protected Attribute attrValue;

	/**
	 * an array of clusterers, one for each node whose domain is to be learnt
	 * via clustering. The clusterers are built from the instance data when the
	 * learning process is ended. Each entry in the array corresponds to an
	 * entry in clusteredDomains.
	 */
	SimpleKMeans[] clusterers;

	/**
	 * an object providing a function for naming clusters
	 */
	protected ClusterNamer<SimpleKMeans> clusterNamer;

	/**
	 * an array of nodes for which the domains
	 * are to be learnt directly from the set of examples (i.e. every value that
	 * occurs in the examples is also a possible outcome in the domain);
	 */
	protected BeliefNode[] directDomains;

	/**
	 * an array of hash sets, where each set contains the outcomes that were
	 * encountered so far for one of the entries in array directDomains
	 */
	protected Vector<HashSet<String>> directDomainData;

	/**
	 * an array of arrays of strings specifying domains that can be transferred
	 * from one node to another; may be null. If several nodes essentially share
	 * the same domain, the domain need only be learnt for one of the nodes; the
	 * learnt domain can then be transferred to the other nodes once the
	 * learning is complete. Each item in the array is an array of strings,
	 * where the first item is the name of the node for which the domain will be
	 * learnt and all subsequent items are the names of nodes to which the
	 * learnt domain is to be
	 */
	String[][] duplicateDomains;

	protected boolean verbose = false;

	/**
	 * holds information on a node whose domain is to be learnt by clustering
	 * 
	 * @author Dominik Jain
	 */
	static public class ClusteredDomain {
		public String nodeName;

		public int numClusters;

		/**
		 * @param nodeName
		 *            the name of the node
		 * @param numClusters
		 *            the number of clusters to learn (or 0 if the number of
		 *            clusters is to be determined automatically)
		 */
		public ClusteredDomain(String nodeName, int numClusters) {
			this.nodeName = nodeName;
			this.numClusters = numClusters;
		}
	}

	/**
	 * constructs a DomainLearner object from a BeliefNetworkEx object
	 * 
	 * @param bn
	 *            the belief network
	 * @param directDomains
	 *            an array of strings containing the names of nodes for which
	 *            the domains are to be learnt directly from the set of examples
	 *            (i.e. every value that occurs in the examples is also a
	 *            possible outcome in the domain); may be null
	 * @param clusteredDomains
	 *            an array of ClusteredDomain objects, where each object
	 *            contains information on a node whose domain is to be learnt
	 *            via clustering; may be null
	 * @param namer
	 *            the namer that is to be used for naming clusters (may be null
	 *            if no clustered domains are specified)
	 * @param duplicateDomains
	 *            an array of arrays of strings specifying domains that can be
	 *            transferred from one node to another; may be null. If several
	 *            nodes essentially share the same domain, the domain need only
	 *            be learnt for one of the nodes; the learnt domain can then be
	 *            transferred to the other nodes once the learning is complete.
	 *            Each item in the array is an array of strings, where the first
	 *            item is the name of the node for which the domain will be
	 *            learnt and all subsequent items are the names of nodes to
	 *            which the learnt domain is to be transferred.
	 * @throws Exception
	 */
	public DomainLearner(BeliefNetworkEx bn, String[] directDomains,
			ClusteredDomain[] clusteredDomains, ClusterNamer<SimpleKMeans> namer,
			String[][] duplicateDomains) throws Exception {
		super(bn);
		init(getBeliefNodes(directDomains), clusteredDomains, namer, duplicateDomains);
	}

	/**
	 * constructs a DomainLearner object from a BeliefNetwork object
	 * 
	 * @param bn
	 *            the belief network
	 * @param directDomains
	 *            an array of strings containing the names of nodes for which
	 *            the domains are to be learnt directly from the set of examples
	 *            (i.e. every value that occurs in the examples is also a
	 *            possible outcome in the domain)
	 * @param clusteredDomains
	 *            an array of ClusteredDomain objects, where each object
	 *            contains information on a node whose domain is to be learnt
	 *            via clustering
	 * @param namer
	 *            the namer that is to be used for naming clusters (may be null
	 *            if no clustered domains are specified)
	 * @param duplicateDomains
	 *            an array of arrays of strings specifying domains that can be
	 *            transferred from one node to another. If several nodes
	 *            essentially share the same domain, the domain need only be
	 *            learnt for one of the nodes; the learnt domain can then be
	 *            transferred to the other nodes once the learning is complete.
	 *            Each item in the array is an array of strings, where the first
	 *            item is the name of the node for which the domain will be
	 *            learnt and all subsequent items are the names of nodes to
	 *            which the learnt domain is to be transferred.
	 * @throws Exception
	 */
	public DomainLearner(BeliefNetwork bn, String[] directDomains,
			ClusteredDomain[] clusteredDomains, ClusterNamer<SimpleKMeans> namer,
			String[][] duplicateDomains) {
		super(bn);
		init(getBeliefNodes(directDomains), clusteredDomains, namer, duplicateDomains);
	}

	/**
	 * constructs a DomainLearner where the domains of all nodes are to be
	 * learnt directly from the set of examples (i.e. every value that occurs in
	 * the examples is also a possible outcome in the domain)
	 * 
	 * @param bn
	 *            the belief network
	 */
	public DomainLearner(BeliefNetwork bn) {
		this(new BeliefNetworkEx(bn));
	}

	/**
	 * constructs a DomainLearner where the domains of all nodes are to be
	 * learnt directly from the set of examples (i.e. every value that occurs in
	 * the examples is also a possible outcome in the domain)
	 * 
	 * @param bn
	 *            the belief network
	 */
	public DomainLearner(BeliefNetworkEx bn) {
		super(bn);
		init(bn.bn.getNodes(), null, null, null);
	}

	protected BeliefNode[] getBeliefNodes(String[] names) {
		BeliefNode[] nodes = new BeliefNode[names.length];
		for(int i = 0; i < names.length; i++)
			nodes[i] = this.bn.getNode(names[i]);
		return nodes;
	}
	
	private void init(BeliefNode[] directDomains,
			ClusteredDomain[] clusteredDomains, ClusterNamer<SimpleKMeans> namer,
			String[][] duplicateDomains) {
		this.clusteredDomains = clusteredDomains;
		attrValue = new Attribute("value");
		if (clusteredDomains != null)
			clusterers = new SimpleKMeans[clusteredDomains.length];
		this.clusterNamer = namer;
		this.directDomains = directDomains;
		this.duplicateDomains = duplicateDomains;

		// create outcome sets for direct domain learning
		if (directDomains != null) {
			directDomainData = new Vector<HashSet<String>>();
			for (int i = 0; i < directDomains.length; i++)
				directDomainData.add(new HashSet<String>());
		}

		// create instance storage for learning of domains using clustering
		if (clusteredDomains != null) {
			clusterData = new Instances[clusteredDomains.length];
			for (int i = 0; i < clusteredDomains.length; i++) {
				FastVector attribs = new FastVector(1);
				attribs.addElement(attrValue);
				clusterData[i] = new Instances(clusteredDomains[i].nodeName,
						attribs, 100);
			}
		}
	}

	/**
	 * learns all the examples in the result set. Each row in the result set
	 * represents one example. All the random variables (nodes) that have been
	 * scheduled for learning in the constructor need to be found in each result
	 * row as columns that are named accordingly, i.e. for each random variable
	 * for which the domain is to be learnt, there must be a column with a
	 * matching name in the result set.
	 * 
	 * @param rs
	 *            the result set
	 * @throws Exception
	 *             if the result set is empty
	 * @throws SQLException
	 *             particularly if there is no matching column for one of the
	 *             node names
	 */
	public void learn(ResultSet rs) throws Exception, SQLException {
		// if it's an empty result set, throw exception
		if (!rs.next())
			throw new Exception("empty result set!");

		// gather domain data
		int numDirectDomains = directDomains != null ? directDomains.length : 0;
		int numClusteredDomains = clusteredDomains != null ? clusteredDomains.length
				: 0;
		do {
			// for direct learning, add outcomes to the set of outcomes
			for (int i = 0; i < numDirectDomains; i++) {
				directDomainData.get(i).add(rs.getString(directDomains[i].getName()));
			}
			// for clustering, gather all instances
			for (int i = 0; i < numClusteredDomains; i++) {
				Instance inst = new Instance(1);
				inst.setValue(attrValue, rs.getDouble(clusteredDomains[i].nodeName));
				clusterData[i].add(inst);
			}
		} while (rs.next());
	}
	
	/**
	 * learns all the examples in the result set. Each row in the result set
	 * represents one example. All the random variables (nodes) that have been
	 * scheduled for learning in the constructor need to be found in each result
	 * row as columns that are named accordingly, i.e. for each random variable
	 * for which the domain is to be learnt, there must be a column with a
	 * matching name in the result set.
	 * 
	 * @param rs
	 *            the result set
	 * @throws Exception
	 *             if the result set is empty
	 * @throws SQLException
	 *             particularly if there is no matching column for one of the
	 *             node names
	 */
	public void learn(Instances instances) throws Exception, SQLException {
		// if it's an empty result set, throw exception
		if(instances.numInstances() == 0)
			throw new Exception("empty result set!");

		// gather domain data
		int numDirectDomains = directDomains != null ? directDomains.length : 0;
		int numClusteredDomains = clusteredDomains != null ? clusteredDomains.length : 0;
		@SuppressWarnings("unchecked")
		Enumeration<Instance> instanceEnum = instances.enumerateInstances();
		while (instanceEnum.hasMoreElements()) {
			Instance instance = instanceEnum.nextElement();
			// for direct learning, add outcomes to the set of outcomes
			for (int i = 0; i < numDirectDomains; i++) {
				directDomainData.get(i).add(instance.stringValue(instances.attribute(directDomains[i].getName())));
			}
			// for clustering, gather all instances
			for (int i = 0; i < numClusteredDomains; i++) {
				Instance inst = new Instance(1);
				inst.setValue(attrValue, instance.value(
						instances.attribute(clusteredDomains[i].nodeName)));
				clusterData[i].add(inst);
			}
		}
	}
	
	/**
	 * learns an example from a HashMap<String,String>.
	 * 
	 * @param data
	 *            a HashMap containing the data for one example. The names of
	 *            the random variables (nodes) for which the domains are to be
	 *            learnt must be found in the set of keys of the hash map.
	 * @throws Exception
	 *             if required keys are missing from the HashMap
	 */
	public void learn(Map<String, String> data) throws Exception {
		int numDirectDomains = directDomains != null ? directDomains.length : 0;
		int numClusteredDomains = clusteredDomains != null ? clusteredDomains.length
				: 0;

		// for direct learning, add outcomes to the set of outcomes
		for (int i = 0; i < numDirectDomains; i++) {
			String val = data.get(directDomains[i]);
			if (val == null)
				throw new Exception("Key " + clusteredDomains[i].nodeName + " not found in data!");
			directDomainData.get(i).add(val);
		}
		// for clustering, gather all instances
		for (int i = 0; i < numClusteredDomains; i++) {
			Instance inst = new Instance(1);
			String val = data.get(clusteredDomains[i].nodeName);
			if (val == null) {
				throw new Exception("Key " + clusteredDomains[i].nodeName + " not found in data!");
			}
			inst.setValue(attrValue, Double.parseDouble(val));
			clusterData[i].add(inst);
		}
	}

	/**
	 * learns all the examples in a fipm.data.QueryResult (otherwise analogous
	 * to learn(ResultSet))
	 * 
	 * @param res
	 *            the query result containing the data for a set of examples
	 * @throws Exception
	 */
	/*
	public void learn(QueryResult res) throws Exception {
		int numDirectDomains = directDomains != null ? directDomains.length : 0;
		int numClusteredDomains = clusteredDomains != null ? clusteredDomains.length
				: 0;

		// get column indices
		Vector colnames = res.getColumnNames();
		int[] colIdx_cd = new int[numClusteredDomains];
		int[] colIdx_dd = new int[numDirectDomains];
		for (int i = 0; i < numClusteredDomains; i++) {
			colIdx_cd[i] = colnames.indexOf(clusteredDomains[i].nodeName);
			if (colIdx_cd[i] == -1)
				throw new Exception("Node/column "
						+ clusteredDomains[i].nodeName
						+ " was not found in result set");
		}
		for (int i = 0; i < numDirectDomains; i++) {
			colIdx_dd[i] = colnames.indexOf(directDomains[i]);
			if (colIdx_dd[i] == -1)
				throw new Exception("Node/column " + directDomains[i]
						+ " was not found in result set");
		}

		// gather data
		for (int i = 0; i < res.getRowCount(); i++) {
			Vector<Object> row = new Vector<Object>();
			for(Object r:res.getRow(i))
					row.add(r);
			// for direct learning, add outcomes to the set of outcomes
			for (int j = 0; j < numDirectDomains; j++) {
				((HashSet<String>) directDomainData[j]).add((String) row
						.get(colIdx_dd[j]));
			}
			// for clustering, gather all instances
			for (int j = 0; j < numClusteredDomains; j++) {
				Instance inst = new Instance(1);
				double value = Double.parseDouble((String) row
						.get(colIdx_cd[j]));
				inst.setValue(attrValue, value);
				clusterData[j].add(inst);
			}
		}
	}
	*/

	/**
	 * performs the clustering (if some domains are to be learnt by clustering)
	 * and applies all the new domains. (This method is called by finish(),
	 * which should be called when all the examples have been passed.)
	 */
	protected void end_learning() throws Exception {
		if (directDomains != null)
			for (int i = 0; i < directDomains.length; i++) {
				if (verbose)
					System.out.println(directDomains[i]);
				HashSet<String> hs = directDomainData.get(i);
				Discrete domain = new Discrete();
				for (Iterator<String> iter = hs.iterator(); iter.hasNext();)
					domain.addName(iter.next());
				BeliefNode node = directDomains[i];
				if (node == null) {
					System.out.println("No node with name '" + directDomains[i]
							+ "' found to learn direct domain for.");
				}
				//System.out.println("DomainLearner: applying domain " + hs + " to " + node.getName());
				bn.bn.changeBeliefNodeDomain(node, domain);
			}
		if (clusteredDomains != null)
			for (int i = 0; i < clusteredDomains.length; i++) {
				if (verbose)
					System.out.println(clusteredDomains[i].nodeName);
				try {
					// perform clustering
					clusterers[i] = new SimpleKMeans();
					if (clusteredDomains[i].numClusters != 0)
						clusterers[i]
								.setNumClusters(clusteredDomains[i].numClusters);
					clusterers[i].buildClusterer(clusterData[i]);
					// update domain
					bn.bn.changeBeliefNodeDomain(bn
							.getNode(clusteredDomains[i].nodeName),
							new Discretized(clusterers[i], clusterNamer));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		if (duplicateDomains != null) {
			for (int i = 0; i < duplicateDomains.length; i++) {
				Domain srcDomain = bn.getDomain(duplicateDomains[i][0]);
				for (int j = 1; j < duplicateDomains[i].length; j++) {
					if (verbose)
						System.out.println(duplicateDomains[i][j]);
					bn.bn.changeBeliefNodeDomain(bn
							.getNode(duplicateDomains[i][j]), srcDomain);
				}
			}
		}
	}

	/**
	 * returns the array clusterers for all the nodes whose domains were to be
	 * learned by clustering
	 * 
	 * @return the array of clusterers. It is ordered according to the array of
	 *         "clustered domains" that was passed at construction.
	 * @throws Exception 
	 */
	public SimpleKMeans[] getClusterers() throws Exception {
		finish(); // make sure learning is completed
		return clusterers;
	}

	/**
	 * sorts the domains that were learned via clustering in ascending order of
	 * cluster centroid. Attention: Do not use the clusterers returned by
	 * getClusterers() after this function has been called because the indices
	 * returned by clusterInstance will otherwise be wrong! In particular, never
	 * conduct CPT-learning after calling this function. (You may, of course,
	 * call this function after the CPT-learning has been completed.)
	 */
	public void sortClusteredDomains() {
		// process all nodes whose domains were subject to clustering
		for (int i = 0; i < clusteredDomains.length; i++) {
			BeliefNode node = bn.getNode(clusteredDomains[i].nodeName);
			sortClusteredDomain(node, clusterers[i]);
		}
		// 
		if (duplicateDomains != null) {
			for (int i = 0; i < duplicateDomains.length; i++)
				for (int j = 0; j < clusteredDomains.length; j++)
					if (duplicateDomains[i][0]
							.equals(clusteredDomains[j].nodeName)) {
						for (int k = 1; k < duplicateDomains[i].length; k++)
							sortClusteredDomain(bn
									.getNode(duplicateDomains[i][k]),
									clusterers[j]);
						break;
					}
		}
	}

	/**
	 * sorts the domain of the given node, for which the given clusterer has
	 * been learnt, in ascending order of cluster centroid
	 * 
	 * @param node
	 * @param clusterer
	 */
	protected void sortClusteredDomain(BeliefNode node, SimpleKMeans clusterer) {
		// get domain sort order (sort by centroid, ascending),
		// i.e. get an unsorted and a sorted version of
		// the centroids array
		int numClusters = clusterer.getNumClusters();
		double[] values = clusterer.getClusterCentroids()
				.attributeToDoubleArray(0);
		double[] sorted_values = (double[]) values.clone();
		Arrays.sort(sorted_values);
		// create new sorted domain
		Discrete domain = (Discrete) node.getDomain();
		Discrete sorted_domain = new Discrete();
		for (int new_idx = 0; new_idx < numClusters; new_idx++) {
			for (int old_idx = 0; old_idx < numClusters; old_idx++)
				if (values[old_idx] == sorted_values[new_idx])
					sorted_domain.addName(domain.getName(old_idx));
		}
		// apply new, sorted domain
		bn.bn.changeBeliefNodeDomain(node, sorted_domain);
	}
}
