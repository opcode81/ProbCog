/*******************************************************************************
 * Copyright (C) 2010-2012 Stefan Waldherr, Dominik Jain.
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
package probcog.bayesnets.inference;

import java.util.HashMap;
import java.util.Vector;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.util.datastruct.MutableDouble;

/**
 * (iterative/loopy) belief propagation 
 * @author Stefan Waldherr
 */
public class BeliefPropagation extends Sampler {

	protected BeliefNode[] nodes;
	protected int[] topOrder;
	protected HashMap<BeliefNode,double[]> lambda;
	protected HashMap<BeliefNode,double[]> pi;
	protected HashMap<BeliefNode, BeliefMessageContainer> messages; //links nodes to their message contaienr
	protected HashMap<BeliefNode,double[]> priors;
	
	public class BeliefMessageContainer{
		public HashMap<BeliefNode, double[]> lambdaMessages;
		public HashMap<BeliefNode, double[]> piMessages;
		protected BeliefNode node;
		protected int nodeOrder;
				
		public BeliefMessageContainer(BeliefNode node){
			// contains all pi and lambda messages that are sent from this node
			lambdaMessages = new HashMap<BeliefNode, double[]>();
			piMessages = new HashMap<BeliefNode, double[]>();
			this.node = node;
			nodeOrder = node.getDomain().getOrder();
			//initialize empty messages
			for (BeliefNode n : bn.bn.getChildren(node)){
				double[] initPi = new double[nodeOrder];
				for (int i = 0; i < nodeOrder; i++){
					initPi[i] = 1.0/nodeOrder;
				}
				piMessages.put(n,initPi);
			}
			for(BeliefNode n : bn.bn.getParents(node)){
				int parentOrder = n.getDomain().getOrder();
				double[] initLambda = new double[parentOrder];
				for (int i = 0; i < parentOrder; i++){
					initLambda[i] = 1.0/parentOrder;
				}
				lambdaMessages.put(n, initLambda);
			}
		}
		
		public void computePiMessages(BeliefNode n){
			double normalize = 0.0;
			for (int i = 0; i < nodeOrder; i++){
				double prod = 1.0;
				for (BeliefNode c : piMessages.keySet()){
					if (c != n){
						prod *= messages.get(c).lambdaMessages.get(node)[i];
					}
				}
				double entry = prod * pi.get(node)[i];
				piMessages.get(n)[i] = entry;
				normalize += entry;
			}
			// normalize
			if (normalize != 0.0){
				if (normalize == 0.0)
					return;
				for (int i = 0; i < nodeOrder; i++){
					piMessages.get(n)[i] /= normalize;
				}
			}
		}
		
		public void computeLambdaMessages(BeliefNode n, int[] nodeDomainIndices) {
			// determine the variables to sum over
			Vector<Integer> varsToSumOver = new Vector<Integer>();
			for (BeliefNode p : lambdaMessages.keySet()){
				if (p != n && nodeDomainIndices[getNodeIndex(p)] == -1){ // TODO for Stefan to ack: replaced bn.getNodeIndex by this.getNodeIndex, because the former is very inefficient, since it constructs the vector of nodes each time and does a linear search
					varsToSumOver.add(getNodeIndex(p));
				}
			}
			// actual calculation of lambda message 
			double normalize = 0.0;
			for (int i = 0; i < lambdaMessages.get(n).length; i++){
				nodeDomainIndices[getNodeIndex(n)] = i;
				double sum = 0.0;
				for (int j = 0; j < nodeOrder; j++){
					nodeDomainIndices[getNodeIndex(node)] = j;
					double prod = lambda.get(node)[j];
					MutableDouble mutableSum = new MutableDouble(0.0);
					computeLambdaMessages(n, varsToSumOver,0,nodeDomainIndices,mutableSum);
					sum += prod * mutableSum.value;
				}
				lambdaMessages.get(n)[i] = sum;
				normalize += sum;
			}
			if (normalize != 0.0){
				if (normalize == 0.0)
					return;
				for (int i = 0; i < lambdaMessages.get(n).length; i++){
					lambdaMessages.get(n)[i] /= normalize;
				}
			}
		}

		protected void computeLambdaMessages(BeliefNode n, Vector<Integer> varsToSumOver, int i, int[] nodeDomainIndices, MutableDouble sum) {
			if (i == varsToSumOver.size()) {
				double result = getCPTProbability(node, nodeDomainIndices);
				// multiply with incoming pi messages
				for (BeliefNode p : bn.bn.getParents(node)){
					if (n != p){
						result *= messages.get(p).piMessages.get(node)[nodeDomainIndices[getNodeIndex(p)]];
					}
				}
				sum.value += result;
				return;
			}
			int idxVar = varsToSumOver.get(i);
			for (int v = 0; v < nodes[idxVar].getDomain().getOrder(); v++) {
				nodeDomainIndices[idxVar] = v;
				computeLambdaMessages(n, varsToSumOver, i + 1, nodeDomainIndices, sum);
			}
		}
		
		public boolean sentPiMessageTo(BeliefNode c){
			if (pi.containsKey(c)){
				double sum = 0.0;
				for (double d : pi.get(c)){
					sum += d;
				}
				return (sum != 0.0);
			}
			else{
				return false;
			}
		}
		
		public boolean sentLambdaMessageTo(BeliefNode p){
			if (lambdaMessages.containsKey(p)){
				double sum = 0.0;
				for (double d : lambdaMessages.get(p)){
					sum += d;
				}
				return (sum != 0.0);
			}
			else{
				return false;
			}
		}
	}
	
	public void computePi(BeliefNode n, int[] nodeDomainIndices){
		// determine the variables to sum over
		if (evidenceDomainIndices[getNodeIndex(n)] != -1)
			return;
		Vector<Integer> varsToSumOver = new Vector<Integer>(); // TODO varsToSumOver doesn't change; if need be, we can cache it 
		for (BeliefNode p : bn.bn.getParents(n)){
			if (nodeDomainIndices[getNodeIndex(p)] == -1){
				varsToSumOver.add(getNodeIndex(p));
			}
		}		
		double normalize = 0.0;
		for (int i = 0; i < pi.get(n).length; i++){
			nodeDomainIndices[getNodeIndex(n)] = i;
			MutableDouble mutableSum = new MutableDouble(0.0);
			computePi(n,varsToSumOver,0,nodeDomainIndices,mutableSum);
			pi.get(n)[i] = mutableSum.value;
			normalize += mutableSum.value;
		}
		if (normalize == 0.0)
			return;
		for (int i = 0; i < pi.get(n).length; i++){
			pi.get(n)[i] /= normalize;
		}
	}
	
	protected void computePi(BeliefNode n, Vector<Integer> varsToSumOver, int i, int[] nodeDomainIndices, MutableDouble sum) {
		if (i == varsToSumOver.size()) {
			double result = getCPTProbability(n, nodeDomainIndices);
			// multiply with incoming pi messages
			for (BeliefNode p : bn.bn.getParents(n)){
				result *= messages.get(p).piMessages.get(n)[nodeDomainIndices[getNodeIndex(p)]];
			}
			sum.value += result;
			return;
		}
		int idxVar = varsToSumOver.get(i);
		for (int v = 0; v < nodes[idxVar].getDomain().getOrder(); v++) {
			nodeDomainIndices[idxVar] = v;
			computePi(n, varsToSumOver, i + 1, nodeDomainIndices, sum);
		}
	}
	
	public void computeLambda(BeliefNode n){
		if (evidenceDomainIndices[getNodeIndex(n)] != -1)
			return;
		double normalize = 0.0;
		for (int i = 0; i < lambda.get(n).length; i++){
			double prod = 1.0;
			for (BeliefNode c : bn.bn.getChildren(n)){
				prod *= messages.get(c).lambdaMessages.get(n)[i];
			}
			lambda.get(n)[i] = prod;
			normalize += prod;
		}
		if (normalize == 0.0)
			return;
		for (int i = 0; i < lambda.get(n).length; i++){
			lambda.get(n)[i] /= normalize;
		}
	}
	
	public BeliefPropagation(BeliefNetworkEx bn) throws Exception {
		super(bn);
		// Initialization of BP
		nodes = bn.getNodes();
		topOrder = bn.getTopologicalOrder();
		lambda = new HashMap<BeliefNode,double[]>();
		pi = new HashMap<BeliefNode,double[]>();
		messages = new HashMap<BeliefNode, BeliefMessageContainer>();
	}
	
	@Override
	public String getAlgorithmName() {
		return String.format("Belief Propagation");
	}

	@Override
	protected void _infer() throws Exception {
		// initialization of lambda and pi.
		priors = bn.computePriors(evidenceDomainIndices);
		for (BeliefNode n : nodes){
			int domSize = n.getDomain().getOrder();
			int domIdx = evidenceDomainIndices[getNodeIndex(n)];
			// build message function hashmap
			messages.put(n, new BeliefMessageContainer(n));
			// initialize probability distribution
			double[] init = new double[domSize];
			for (int i = 0; i < init.length; i++){
				init[i] = 0.0;
			}
			// initialize evidence variables
			if (domIdx != -1){
				init[domIdx] = 1.0;
				lambda.put(n, init.clone());
				pi.put(n, init.clone());
			}
					
			else{
				// initialize nodes without parents
				if (bn.bn.getParents(n).length == 0){
					double[] prior = priors.get(n);
					pi.put(n, prior);
				}
				// initialize nodes without children
				if (bn.bn.getChildren(n).length == 0){
					double normalized = 1 / (double) domSize;
					double[] uniform = new double[domSize];
					for (int i = 0; i < uniform.length; i++){
						uniform[i] = normalized;
					}
					lambda.put(n, uniform);
				}
				if (!pi.containsKey(n)){
					pi.put(n, init.clone());
				}
				if (!lambda.containsKey(n)){
					lambda.put(n, init.clone());
				}
			}
		}
		
		if (debug){
			out.println("After initialization process");
			for (BeliefNode n : nodes){
				out.println(" Node: " + n);
				out.println("   Pi(x):" + n);
				for(int i = 0; i < pi.get(n).length; i++){
					out.println("    " + i + ": " + pi.get(n)[i]);
				}
				out.println("   Lambda(x):" + n);
				for(int i = 0; i < lambda.get(n).length; i++){
					out.println("    " + i + ": " + lambda.get(n)[i]);
				}
			}
		}
		
		// Belief Propagation Steps		
		
		for (int step = 1; step <= this.numSamples; step++) {
			
			if(verbose && step % this.infoInterval == 0)
				out.println("step " + step);
			
			// calculate pi(x)
			for (BeliefNode n : nodes){
				int[] nodeDomainIndices = evidenceDomainIndices.clone(); // TODO why do we clone each time? Isn't it enough to have one copy to work with for all nodes.
																		 // Not cloning this lead to complications in the IJGP algo so I continued cloning here.
				boolean receivedAll = true;
				// check whether n has received all pi messages from its parents
				for (BeliefNode c : bn.bn.getParents(n)){
					double sum = 0.0;
					for (double d : messages.get(c).piMessages.get(n)){
						sum += d;
					}
					if (sum == 0.0){
						receivedAll = false;
					}
				}
				if (receivedAll){
					computePi(n, nodeDomainIndices);
				}
			}
			
			// calculate lambda(x):
			for (BeliefNode n : nodes){
				boolean receivedAll = true;
				//check whether n has received all lambda messages from its children
				for (BeliefNode c : bn.bn.getChildren(n)){
					double sum = 0.0;
					for (double d : messages.get(c).lambdaMessages.get(n)){
						sum += d;
					}
					if (sum == 0.0){
						receivedAll = false;
						break;
					}
				}
				if (receivedAll && bn.bn.getChildren(n).length > 0){
					computeLambda(n);
				}
			}
			
			// calculate outgoing pi messages for every node
			for (BeliefNode n : nodes){
				//if pi has been calculated...
				double sum = 0.0;
				for (double d : pi.get(n)){
					sum += d;
				}
				if (sum != 0){
					BeliefNode[] children = bn.bn.getChildren(n); // TODO for Stefan to ack: getChildren was called twice, should avoid because it's an expensive call (always reallocates the vector of children)
					for (BeliefNode c : children){
						// ... and lambda received from all children except c
						boolean receivedAll = true;
						for (BeliefNode c2 : children){
							if ((c2 != c) && !messages.get(c2).sentLambdaMessageTo(n)){
								receivedAll = false;
								break;
							}
						}
						if (receivedAll){
							messages.get(n).computePiMessages(c);
						}
					}
				}
			}
			
			// calculate outgoing lambda messages for every node
			for (BeliefNode n : nodes){
				//if lambda has been calculated...
				double sum = 0.0;
				for (double d : lambda.get(n)){
					sum += d;
				}
				if (sum != 0){
					for (BeliefNode p : bn.bn.getParents(n)){ // TODO note: a slightly better way of getting the parents (because it doesn't allocate any memory) is to use getCPF().getDomainProduct and iterate over the elements 1 to end
						// ... and pi received from all parents except p
						boolean receivedAll = true;
						for (BeliefNode p2 : bn.bn.getParents(n)){
							if ((p2 != p) && !messages.get(p2).sentPiMessageTo(n)){
								receivedAll = false;
								break;
							}
						}
						if (receivedAll){
							int[] nodeDomainIndices = evidenceDomainIndices.clone();
							messages.get(n).computeLambdaMessages(p, nodeDomainIndices);
						}
					}
				}
			}
			
			if(debug){
				out.println("\n\n****After step " + step + "****");
				out.println("\n     Pi and Lambda Functions");
				for (BeliefNode n : nodes){
					out.println(" Node: " + n);
					out.println("   Pi(x):" + n);
					for(int i = 0; i < pi.get(n).length; i++){
						out.println("    " + i + ": " + pi.get(n)[i]);
					}
					out.println("   Lambda(x):" + n);
					for(int i = 0; i < lambda.get(n).length; i++){
						out.println("    " + i + ": " + lambda.get(n)[i]);
					}
				}
				
				out.println("\n     Message Functions");
				for (BeliefNode n : nodes){
					out.println("Node: " + n);
					for (BeliefNode c : messages.get(n).piMessages.keySet()){
						out.println("   Pi-Message to " + c + ":");
						for(int i = 0; i < messages.get(n).piMessages.get(c).length; i++){
							out.println("    " + i + ": " + messages.get(n).piMessages.get(c)[i]);
						}
					}
					for (BeliefNode c : messages.get(n).lambdaMessages.keySet()){
						out.println("   Lambda to (x):" + c);
						for(int i = 0; i < messages.get(n).lambdaMessages.get(c).length; i++){
							out.println("    " + i + ": " + messages.get(n).lambdaMessages.get(c)[i]);
						}
					}
				}
			}
			
		}
		// compute probabilities and store results in distribution
		if(verbose) out.println("computing results....");
		SampledDistribution dist = createDistribution();
		dist.Z = 1.0;
		for (BeliefNode n : nodes) {
			int i = getNodeIndex(n);
			if (evidenceDomainIndices[i] >= 0) {
				dist.values[i][evidenceDomainIndices[i]] = 1.0;
				continue;
			}
			int domSize = dist.values[i].length;
			double normalize = 0.0;
			for (int j = 0; j < domSize; j++) {
				dist.values[i][j] = lambda.get(n)[j]*pi.get(n)[j];
				normalize += dist.values[i][j]; 
			}
			for (int j = 0; j < domSize; j++) {
				if (normalize == 0.0)
					continue;
				dist.values[i][j] /= normalize;
			}
		}
		((ImmediateDistributionBuilder)distributionBuilder).setDistribution(dist);
	}

	protected IDistributionBuilder createDistributionBuilder() {
		return new ImmediateDistributionBuilder();
	}
}
