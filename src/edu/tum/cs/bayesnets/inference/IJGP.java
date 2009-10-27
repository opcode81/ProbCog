/*
 * Created on Sep 29, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.tools.MutableDouble;

public class IJGP extends Sampler {

	protected JoinGraph jg;
	Vector<JoinGraph.Node> jgNodes;
	protected BeliefNode[] nodes;

	protected static class Cluster {
		HashSet<BeliefNode> cpts;
		HashSet<MessageFunction> functions;

		public Cluster(JoinGraph.Node n) {
			cpts.addAll(n.nodes);
			for (JoinGraph.Node nb : n.getNeighbors()) {
				MessageFunction m = n.arcs.get(nb).getInMessage(n);
				if (m != null)
					functions.add(m);
			}
		}

		public void excludeMessage(MessageFunction m) {
			if(functions.contains(m))
				functions.remove(m);
		}

		public Cluster clone() {
			return this.clone();
		}

		public Cluster getReducedCluster(HashSet<BeliefNode> nodes) {
			// deletes all functions and arcs in the cluster whose scope
			// contains the given nodes
			Cluster redCluster = this.clone();
			for (BeliefNode bn : nodes) {
				for (BeliefNode n : cpts) {
					BeliefNode[] domProd = n.getCPF().getDomainProduct();
					for (int i = 0; i < domProd.length; i++) {
						if (bn.equals(domProd[i])) {
							redCluster.cpts.remove(n);
							break;
						}
					}
				}
				for (MessageFunction m : functions) {
					if (m.scope.contains(bn))
						redCluster.functions.remove(m);
				}
			}
			return redCluster;
		}

		public void subtractCluster(Cluster c2) {
			// deletes all functions and arcs of the cluster that are also in
			// cluster c2
			for (BeliefNode n : c2.cpts) {
				cpts.remove(n);
			}
			for (MessageFunction m : c2.functions) {
				functions.remove(m);
			}
		}
	}

	public IJGP(BeliefNetworkEx bn, int bound) {
		super(bn);
		nodes = bn.bn.getNodes();
		jg = new JoinGraph(bn, bound);
		jgNodes = jg.getTopologicalorder();
		// construct join-graph
	}

	@Override
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {
		// process observed variables
		for (JoinGraph.Node n : jgNodes) {
			for (BeliefNode belNode : n.nodes) {
				int nodeIdx = bn.getNodeIndex(belNode);
				int domainIdx = evidenceDomainIndices[nodeIdx];
				if (domainIdx > -1)
					n.nodes.remove(belNode);
			}
		}
		// for every node in JG in order and back:
		int s = jgNodes.size();
		for (int j = 0; j < 2*s; j++){
			int i;
			if (j < s)
				i = j;
			else
				i = s-j-1;
			JoinGraph.Node u = jgNodes.get(i);	
			for (JoinGraph.Node v : jgNodes.get(i).getNeighbors()){
				// construct cluster_n
				Cluster cluster_n = new Cluster(u);
				cluster_n.excludeMessage(u.arcs.get(v).getInMessage(u));
				// Include in cluster_H each function in cluster_n which scope does not contain variables in elim(u,v)
				HashSet<BeliefNode> elim = new HashSet<BeliefNode>(jgNodes.get(i).nodes);			
				elim.removeAll(u.getArcToNode(v).seperator);
				Cluster cluster_H = cluster_n.getReducedCluster(elim);
				// denote by cluster_A the remaining functions
				Cluster cluster_A = cluster_n.clone();
				cluster_A.subtractCluster(cluster_H);
				// convert eliminator into varToSumOver
				int[] varsToSumOver = new int[elim.size()];
				Vector<BeliefNode> elimV = new Vector<BeliefNode>(elim);
				for(int k = 0; k < elim.size(); i++)
					varsToSumOver[k] = bn.getNodeIndex(elimV.get(i));
				// create message function and send to v
				MessageFunction m = new MessageFunction(u.getArcToNode(v).seperator, varsToSumOver, cluster_A);
				u.getArcToNode(v).setOutMessage(u, m);			
			}
		}
		
		// compute probabilties and store results in distribution
		System.out.println("reading results...");
		this.createDistribution();
		for (int i = 0; i < nodes.length; i++) {
			// For every node X let u be a vertex in the join graph that X is in u
			JoinGraph.Node u = null;
			for (JoinGraph.Node node : jgNodes){
				if(node.nodes.contains(nodes[i])){
					u = node;
					break;
				}
			}
			// compute probability
			// TODO
		}
		dist.Z = 1.0;
		return dist;

		// createDistribution();
		// do it	
	}

	protected class MessageFunction {

		protected int[] varsToSumOver;
		HashSet<BeliefNode> cpts;
		Iterable<MessageFunction> childFunctions;
		HashSet<BeliefNode> scope;

		public MessageFunction(HashSet<BeliefNode> scope, int[] varsToSumOver, Cluster cluster) {
			this.scope = scope;
			this.varsToSumOver = varsToSumOver;
			this.cpts = cluster.cpts;
			this.childFunctions = cluster.functions;
			
		}

		public double compute(int[] nodeDomainIndices) {
			MutableDouble result = new MutableDouble(0.0);
			compute(varsToSumOver, 0, nodeDomainIndices.clone(), result);
			return result.value;
		}

		protected void compute(int[] varsToSumOver, int i,
				int[] nodeDomainIndices, MutableDouble sum) {
			if (i == varsToSumOver.length) {
				double result = 1.0;
				for (BeliefNode node : cpts)
					result *= getCPTProbability(node, nodeDomainIndices);
				for (MessageFunction h : childFunctions)
					result *= h.compute(nodeDomainIndices);
				sum.value += result;
				return;
			}
			int idxVar = varsToSumOver[i];
			for (int v = 0; v < nodes[idxVar].getDomain().getOrder(); v++) {
				nodeDomainIndices[idxVar] = v;
				compute(varsToSumOver, i + 1, nodeDomainIndices, sum);
			}
		}
	}

	protected static class BucketVar {

		public HashSet<BeliefNode> nodes;
		public CPF cpf = null;
		public MiniBucket parent;
		public BeliefNode idxVar;

		public BucketVar(HashSet<BeliefNode> nodes) {
			this.nodes = nodes;
			this.parent = null;
		}

		public BucketVar(HashSet<BeliefNode> nodes, MiniBucket parent) {
			this.nodes = nodes;
			this.parent = parent;
		}

		public void setFunction(CPF cpf) {
			this.cpf = cpf;
		}

		public void setInArrow(MiniBucket parent) {
			this.parent = parent;
		}

		public BeliefNode getMaxNode(BeliefNetworkEx bn) {
			// returns the BeliefNode of a bucket variable highest in the
			// topological order
			int maxInt = 0;
			BeliefNode maxNode = null;
			for (BeliefNode node : nodes) {
				int newInt = bn.getNodeIndex(node);
				if (newInt > maxInt) {
					maxInt = newInt;
					maxNode = node;
				}
			}
			return maxNode;
		}
	}

	protected static class MiniBucket {

		public HashSet<BucketVar> items;
		public Bucket bucket;
		public HashSet<MiniBucket> parents;
		public BucketVar child;

		public MiniBucket(Bucket bucket) {
			this.items = new HashSet<BucketVar>();
			this.bucket = bucket;
			this.child = null;
			this.parents = null;
		}

		public void addVar(BucketVar bv) {
			items.add(bv);
			if (bv.parent != null)
				parents.add(bv.parent);
		}
	}

	protected static class Bucket {

		public BeliefNode bucketNode;
		public HashSet<BucketVar> vars = new HashSet<BucketVar>();
		public Vector<MiniBucket> minibuckets = new Vector<MiniBucket>();

		public Bucket(BeliefNode bucketNode) {
			this.bucketNode = bucketNode;
		}

		public void addVar(BucketVar bv) {
			vars.add(bv);
		}

		public void partition(int bound) {
			int i = 0;
			for (BucketVar bv : vars) {
				if (i % bound == 0 && i != 0) {
					minibuckets.add(new MiniBucket(this));
				}
				minibuckets.lastElement().addVar(bv);
			}
		}

		public HashSet<BucketVar> createScopeFunctions() {
			HashSet<BucketVar> newVars = new HashSet<BucketVar>();
			for (MiniBucket mb : minibuckets) {
				HashSet<BeliefNode> nodes = new HashSet<BeliefNode>();
				for (BucketVar bv : mb.items) {
					for (BeliefNode bn : bv.nodes) {
						if (bn != bucketNode)
							nodes.add(bn);
					}
				}
				BucketVar newBucketVar = new BucketVar(nodes, mb);
				newVars.add(newBucketVar);
			}
			return newVars;
		}
	}

	protected static class SchematicMiniBucket {

		public HashMap<BeliefNode, Bucket> bucketMap;

		public SchematicMiniBucket(BeliefNetworkEx bn, int bound) {
			bucketMap = new HashMap<BeliefNode, Bucket>();
			// order the variables from X_1 to X_n
			int[] topOrder = bn.getTopologicalOrder();
			BeliefNode[] nodes = bn.bn.getNodes();
			// place each CPT in the bucket of the highest index
			for (int i = topOrder.length - 1; i > -1; i--) {
				Bucket bucket = new Bucket(nodes[i]);
				int[] cpt = bn.getDomainProductNodeIndices(nodes[i]);
				HashSet<BeliefNode> cptNodes = new HashSet<BeliefNode>();
				cptNodes.add(nodes[i]);
				for (int j : cpt) {
					cptNodes.add(nodes[j]);
				}
				BucketVar bv = new BucketVar(cptNodes);
				bv.setFunction(nodes[i].getCPF());
				bucket.addVar(bv);
				bucketMap.put(nodes[i], bucket);
			}
			// partition buckets and create arcs
			for (int i = topOrder.length - 1; i > -1; i--) {
				bucketMap.get(nodes[i]).partition(bound);
				HashSet<BucketVar> scopes = bucketMap.get(nodes[i])
						.createScopeFunctions();
				for (BucketVar bv : scopes) {
					BeliefNode node = bv.getMaxNode(bn);
					bucketMap.get(node).addVar(bv);
				}
			}
		}

		public Vector<MiniBucket> getMiniBuckets() {
			Vector<MiniBucket> mb = new Vector<MiniBucket>();
			for (Bucket b : bucketMap.values()) {
				mb.addAll(b.minibuckets);
			}
			return mb;
		}

		public Vector<Bucket> getBuckets() {
			return new Vector<Bucket>(bucketMap.values());
		}
	}

	protected static class JoinGraph {

		HashSet<Node> nodes;
		HashMap<MiniBucket, Node> bucket2node = new HashMap<MiniBucket, Node>();

		public JoinGraph(BeliefNetworkEx bn, int bound) {
			nodes = new HashSet<Node>();
			// apply procedure schematic mini-bucket(bound)
			SchematicMiniBucket smb = new SchematicMiniBucket(bn, bound);
			Vector<MiniBucket> minibuckets = smb.getMiniBuckets();
			// associate each minibucket with a node
			for (MiniBucket mb : minibuckets) {
				Node newNode = new Node(mb);
				nodes.add(newNode);
				bucket2node.put(mb, newNode);
			}
			// keep the arcs and label them by regular separator
			for (MiniBucket mb : minibuckets) {
				for (MiniBucket par : mb.parents) {
					new Arc(bucket2node.get(par), bucket2node.get(mb));
				}
			}
			// conntect the mini-bucket clusters
			for (MiniBucket mb1 : minibuckets) {
				for (MiniBucket mb2 : minibuckets) {
					if (mb1 != mb2 && mb1.bucket == mb2.bucket) {
						new Arc(bucket2node.get(mb1), bucket2node.get(mb2));
					}
				}
			}
		}

		protected Node merge(Node u, Node v) {
			return null;
		}

		public Vector<Node> getTopologicalorder() {
			// implement
			return new Vector<Node>(nodes);
		}

		public static class Arc {
			Vector<Node> nodes = new Vector<Node>();
			HashSet<BeliefNode> seperator = new HashSet<BeliefNode>();
			// messages between Nodes
			MessageFunction messageN0N1 = null;
			MessageFunction messageN1N0 = null;

			public Arc(Node n0, Node n1) {
				if (n0 != n1) {
					nodes.add(n0);
					nodes.add(n1);
					// create separator
					if (n0.mb.bucket == n1.mb.bucket)
						seperator.add(n0.mb.bucket.bucketNode);
					else {
						for (BeliefNode bn : n0.nodes) {
							if (n1.nodes.contains(bn))
								seperator.add(bn);
						}
					}
					n0.addArc(n0, this);
					n1.addArc(n1, this);
				}
			}

			public Node getNeighbor(Node n) {
				// needs to throw exception when n not in nodes
				return nodes.get((nodes.indexOf(n) + 1) % 2);
			}

			public void setOutMessage(Node n, MessageFunction m) {
				int idx = nodes.indexOf(n);
				if (idx == 0)
					messageN0N1 = m;
				else
					messageN1N0 = m;
			}

			public MessageFunction getOutMessage(Node n) {
				int idx = nodes.indexOf(n);
				if (idx == 0)
					return (messageN0N1);
				else
					return (messageN1N0);
			}

			public MessageFunction getInMessage(Node n) {
				int idx = nodes.indexOf(n);
				if (idx == 0)
					return (messageN1N0);
				else
					return (messageN0N1);
			}

		}

		public static class Node {
			MiniBucket mb;
			Vector<CPF> functions = new Vector<CPF>();
			HashSet<BeliefNode> nodes = new HashSet<BeliefNode>();
			HashMap<Node, Arc> arcs = new HashMap<Node, Arc>();

			public Node(MiniBucket mb) {
				this.mb = mb;
				for (BucketVar var : mb.items) {
					nodes.addAll(var.nodes);
					if (var.cpf != null)
						functions.add(var.cpf);
				}
			}

			public void addArc(Node n, Arc arc) {
				arcs.put(n, arc);
			}

			public HashSet<Node> getNeighbors() {
				return new HashSet<Node>(arcs.keySet());
			}

			public Arc getArcToNode(Node n) {
				return arcs.get(n);
			}
		}
	}
}
