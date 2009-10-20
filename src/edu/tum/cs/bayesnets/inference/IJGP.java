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
import edu.tum.cs.util.datastruct.MutableDouble;

public class IJGP extends Sampler {

	protected JoinGraph jg;
	protected BeliefNode[] nodes;
	
	public IJGP(BeliefNetworkEx bn, int bound) {
		super(bn);
		nodes = bn.bn.getNodes();
		jg = new JoinGraph(bn, bound);
		// construct join-graph
	}

	@Override
	public SampledDistribution infer(int[] evidenceDomainIndices) throws Exception {

		Vector<JoinGraph.Node> nodes = jg.getTopologicalorder();
		
		// process evidence variables: remove them from the supernodes
		for (JoinGraph.Node n : nodes){
			//process observed variables
			Vector<CPF> cpf = n.functions;
			for (BeliefNode belNode : n.nodes){
				int nodeIdx = bn.getNodeIndex(belNode);
				int domainIdx = evidenceDomainIndices[nodeIdx];
			}
		}
		
		// compute individual functions
		
		
		createDistribution();
		// fill it
		return dist;		
	}

	protected class MessageFunction {

		protected int[] varsToSumOver;
		BeliefNode[] cpts;
		Iterable<MessageFunction> childFunctions;

		public MessageFunction() {
			// TODO
		}
		
		public double compute(int[] nodeDomainIndices) {
			MutableDouble result = new MutableDouble(0.0);
			compute(varsToSumOver, 0, nodeDomainIndices.clone(), result);
			return result.value;
		}
		
		protected void compute(int[] varsToSumOver, int i, int[] nodeDomainIndices, MutableDouble sum) {
			if(i == varsToSumOver.length) {
				double result = 1.0;
				for(BeliefNode node : cpts)
					result *= getCPTProbability(node, nodeDomainIndices);			
				for(MessageFunction h : childFunctions)
					result *= h.compute(nodeDomainIndices);
				sum.value += result;
				return;
			}
			int idxVar = varsToSumOver[i];
			for(int v = 0; v < nodes[idxVar].getDomain().getOrder(); v++) {
				nodeDomainIndices[idxVar] = v;
				compute(varsToSumOver, i+1, nodeDomainIndices, sum);
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
			// returns the BeliefNode of a bucket variable highest in the topological order
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
		HashMap<MiniBucket,Node> bucket2node = new HashMap<MiniBucket,Node>();

		public JoinGraph(BeliefNetworkEx bn, int bound) {
			nodes = new HashSet<Node>();
			// apply procedure schematic mini-bucket(bound)
			SchematicMiniBucket smb = new SchematicMiniBucket(bn, bound);
			Vector<MiniBucket> minibuckets = smb.getMiniBuckets();
			// associate each minibucket with a node
			for (MiniBucket mb : minibuckets) {
				Node newNode = new Node(mb);
				nodes.add(newNode);	
				bucket2node.put(mb,newNode);
			}
			// keep the arcs and label them by regular separator
			for (MiniBucket mb: minibuckets){
				for (MiniBucket par : mb.parents){
					new Arc(bucket2node.get(par),bucket2node.get(mb));
				}
			}
			// conntect the mini-bucket clusters
			for (MiniBucket mb1 : minibuckets){
				for (MiniBucket mb2 : minibuckets){
					if (mb1 != mb2 && mb1.bucket == mb2.bucket){
						new Arc(bucket2node.get(mb1),bucket2node.get(mb2));
					}
				}
			}
		}

		protected Node merge(Node u, Node v) {
			return null;
		}
		
		public Vector<Node> getTopologicalorder(){
			//implement
			return new Vector<Node>(nodes);
		}

		public static class Arc {
			Node[] nodes = new Node[2];
			HashSet<BeliefNode> seperator = new HashSet<BeliefNode>();
			
			public Arc(Node n1, Node n2){
 				if(n1 != n2){
					nodes[0] = n1;
	 				nodes[1] = n2;
	 				// create separator
	 				if (n1.mb.bucket == n2.mb.bucket)
	 					seperator.add(n1.mb.bucket.bucketNode);
	 				else{
		 				for(BeliefNode bn : n1.nodes){
		 					if (n2.nodes.contains(bn))
		 						seperator.add(bn);
		 				}
	 				}
	 				n1.addArc(this);
	 				n2.addArc(this);
 				}
			}
		}

		public static class Node {
			MiniBucket mb;
			Vector<CPF> functions = new Vector<CPF>();
			HashSet<BeliefNode> nodes = new HashSet<BeliefNode>();
			HashSet<Arc> arcs = new HashSet<Arc>();
			
			public Node(MiniBucket mb){
				this.mb = mb;
				for (BucketVar var : mb.items) {
					nodes.addAll(var.nodes);
					if(var.cpf != null)
						functions.add(var.cpf);
				}
			}
			
			public void addArc(Arc arc){
				arcs.add(arc);
			}
		}
	}
}
