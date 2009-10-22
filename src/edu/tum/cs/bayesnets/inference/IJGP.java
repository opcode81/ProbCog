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

public class IJGP extends Sampler {

	protected JoinGraph jg;
	
	protected static class Cluster{
		HashSet<CPF> functions;
		HashMap<JoinGraph.Node,JoinGraph.Arc> arcs;
		
		public Cluster(JoinGraph.Node n){
			functions.addAll(n.functions);
			for (JoinGraph.Node nb : n.getNeighbors()){
				arcs.put(nb,n.getArcToNode(nb));
			}
		}
		
		public void excludeMessage(JoinGraph.Node n){
			arcs.remove(n);
		}
		
		public Cluster clone(){
			return this.clone();
		}
		
		public Cluster getReducedCluster(HashSet<BeliefNode> nodes){
			// deletes all functions and arcs in the cluster whose scope contains the given nodes
			Cluster redCluster = this.clone();
			for (BeliefNode bn : nodes){
				for (CPF cpf : functions){
					BeliefNode[] domProd = cpf.getDomainProduct();
					for (int i = 0; i < domProd.length; i++){
						if (bn.equals(domProd[i])){
							redCluster.functions.remove(cpf);
							break;
						}
					}
				}
				for (JoinGraph.Node arcNode : arcs.keySet()){
					if (arcs.get(arcNode).seperator.contains(bn))
						redCluster.arcs.remove(arcNode);
				}
			}
			return redCluster;
		}
		
		public void subtractCluster(Cluster c2){
			// deletes all functions and arcs of the cluster that are also in cluster c2
			for (CPF cpf : c2.functions){
				functions.remove(cpf);
			}
			for (JoinGraph.Node n : c2.arcs.keySet()){
				arcs.remove(n);
			}
		}
		
		public double getClusterProduct(int[] evidenceDomainIndices){
			double d = 1.0;
			/*for (CPF cpf : functions){
				// from BeliefNetworkEx.getCPTProbability - better way?
				int[] domainProduct = getDomainProductNodeIndices(node);
				int[] address = new int[domainProduct.length];
				for (int i = 0; i < address.length; i++) {
					address[i] = evidenceDomainIndices[domainProduct[i]];
				}
				int realAddress = cpf.addr2realaddr(address);
				d *= cpf.getDouble(realAddress);
			}*/
			return d;
		}
	}

	public IJGP(BeliefNetworkEx bn, int bound) {
		super(bn);
		jg = new JoinGraph(bn, bound);
		// construct join-graph
	}

	@Override
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {
		Vector<JoinGraph.Node> nodes = jg.getTopologicalorder();
		// process observed variables
		for (JoinGraph.Node n : nodes) {
			for (BeliefNode belNode : n.nodes) {
				int nodeIdx = bn.getNodeIndex(belNode);
				int domainIdx = evidenceDomainIndices[nodeIdx];
				if (domainIdx > -1)
					n.nodes.remove(belNode);
			}
		}
		// for every node in JG in order and back:
		int s = nodes.size();
		for (int j = 0; j < 2*s; j++){
			int i;
			if (j < s)
				i = j;
			else
				i = s-j-1;
			JoinGraph.Node u = nodes.get(i);	
			for (JoinGraph.Node v : nodes.get(i).getNeighbors()){
				// construct cluster_n
				Cluster cluster_n = new Cluster(u);
				cluster_n.excludeMessage(v);
				// Include in cluster_H each function in cluster_n which scope does not contain variables in elim(u,v)
				HashSet<BeliefNode> elim = new HashSet<BeliefNode>(nodes.get(i).nodes);			
				elim.removeAll(u.getArcToNode(v).seperator);
				Cluster cluster_H = cluster_n.getReducedCluster(elim);
				// denote by cluster_A the remaining functions
				Cluster cluster_A = cluster_n.clone();
				cluster_A.subtractCluster(cluster_H);
				// compute and send to v the combined funtion
				double h = 0.0;
				
				// wrong, todo
				for (BeliefNode bn : elim){
					h += cluster_A.getClusterProduct(evidenceDomainIndices);
				}
				// send h and the individual functions H to node v
				u.getArcToNode(v).setOutMessage(u, h);
			}
		}
		
		// compute probabilties and store results in distribution
		System.out.println("reading results...");
		this.createDistribution();
		BeliefNode[] beliefNodes = bn.bn.getNodes();
		for (int i = 0; i < beliefNodes.length; i++) {
			// For every node X let u be a vertex in the join graph that X is in u
			JoinGraph.Node u;
			for (JoinGraph.Node node : nodes){
				if(node.nodes.contains(beliefNodes[i])){
					u = node;
					break;
				}
			}
			// compute probability
			double p = 0.0;
			//dist.values[i] = values;
		}
		dist.Z = 1.0;
		return dist;

		// createDistribution();
		// do it	
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
			//messages between Nodes
			double messageN0N1 = 1;
			double messageN1N0 = 1;
			

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
					n0.addArc(n0,this);
					n1.addArc(n1,this);
				}
			}
			
			public Node getNeighbor(Node n){
				// needs to throw exception when n not in nodes
				return nodes.get((nodes.indexOf(n)+1)%2);
			}
			
			public void setOutMessage(Node n,double m){
				int idx = nodes.indexOf(n);
				if (idx == 0)
					messageN0N1 = m;
				else
					messageN1N0 = m;
			}
			
			public double getOutMessage(Node n){
				int idx = nodes.indexOf(n);
				if (idx == 0)
					return(messageN0N1);
				else
					return(messageN1N0);
			}
			
			public double getInMessage(Node n){
				int idx = nodes.indexOf(n);
				if (idx == 0)
					return(messageN1N0);
				else
					return(messageN0N1);
			}
			
		}

		public static class Node {
			MiniBucket mb;
			Vector<CPF> functions = new Vector<CPF>();
			HashSet<BeliefNode> nodes = new HashSet<BeliefNode>();
			HashMap<Node,Arc> arcs = new HashMap<Node,Arc>();

			public Node(MiniBucket mb) {
				this.mb = mb;
				for (BucketVar var : mb.items) {
					nodes.addAll(var.nodes);
					if (var.cpf != null)
						functions.add(var.cpf);
				}
			}

			public void addArc(Node n,Arc arc) {
				arcs.put(n,arc);
			}
			
			public HashSet<Node> getNeighbors(){
				return new HashSet<Node>(arcs.keySet());
			}
			
			public Arc getArcToNode(Node n){
				return arcs.get(n);
			}
		}
	}
}
