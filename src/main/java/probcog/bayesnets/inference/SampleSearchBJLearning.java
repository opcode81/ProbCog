/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain.
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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.TreeSet;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.exception.ProbCogException;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.util.datastruct.Pair;
import edu.tum.cs.util.datastruct.PrioritySet;


/**
 * SampleSearch with conflict-directed backjumping and propositional constraint learning (using earliest minimal conflict sets)
 * @author Dominik Jain
 */
public class SampleSearchBJLearning extends SampleSearchBJ {
	protected HighestFirst highestFirst = new HighestFirst();  
	protected NoGoods noGoods = new NoGoods();
	protected int numNoGoods = 0;
	protected boolean useNoGoods = true;
	protected int maxNoGoodSize = 0, totalNoGoodSize = 0;
	protected int numNoGoodNodeChecks = 0;
	private HashSet<NoGood> verifiedNoGoods;
	/**
	 * whether to verify recorded nogoods with regular SampleSearch; applies only when debug is on
	 */
	private boolean verifyNoGoods = false;
	
	public SampleSearchBJLearning(BeliefNetworkEx bn) throws ProbCogException {
		super(bn);			
		this.paramHandler.add("useNoGoods", "setUseNoGoods");
		this.paramHandler.add("verifyNoGoods", "setVerifyNoGoods");
	}
	
	public void setUseNoGoods(boolean enabled) {
		useNoGoods = enabled;
	}

	public void setVerifyNoGoods(boolean enabled) {
		verifyNoGoods = enabled;
	}

	protected class NoGood {
		public int domIdx;
		protected Map<Integer,Pair<Integer,Integer>> nodeSettings;
		protected final boolean debugNoGoodMatching = true;
		
		public NoGood(int domIdx) {
			this.domIdx = domIdx;
			nodeSettings = new TreeMap<Integer, Pair<Integer,Integer>>(highestFirst); // use a tree map to have the domain (keys) ordered
		}
		
		public void addSetting(int nodeIdx, int domIdx) {
			nodeSettings.put(node2orderIndex.get(nodes[nodeIdx]), new Pair<Integer,Integer>(nodeIdx, domIdx));
		}
		
		public void addSettingOrderIdx(int orderIdx, int domIdx) {
			nodeSettings.put(orderIdx, new Pair<Integer, Integer>(nodeOrder[orderIdx], domIdx));
		}
		
		public boolean isApplicable(int[] nodeDomainIndices) {
			if(debugNoGoodMatching && debug) System.out.println("      checking " + this);
			for(Pair<Integer,Integer> e : nodeSettings.values()) {
				numNoGoodNodeChecks++;
				if(nodeDomainIndices[e.first] != e.second) {
					if(debugNoGoodMatching && debug) System.out.printf("        not applicable because %s=%d (should be %d)\n", nodes[e.first], nodeDomainIndices[e.first], e.second);
					return false;
				}
			}
			return true;
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder(String.format("NoGood#%X(", System.identityHashCode(this)));
			sb.append(domIdx);
			sb.append("; ");
			int i = 0; 
			for(Pair<Integer,Integer> e : nodeSettings.values()) {
				if(i++ > 0) sb.append(", ");
				sb.append(nodes[e.first].toString());
				sb.append("=");
				sb.append(nodes[e.first].getDomain().getName(e.second));
			}
			sb.append(")");
			return sb.toString();
		}
		
		/**
		 * @return the collection of variable order indices referenced by this constraint
		 */
		public Collection<Integer> getDomain() {
			return nodeSettings.keySet();
		}
		
		public String getDomainString() {
			Iterator<Integer> i = getDomain().iterator();
			if(!i.hasNext())
				return "";
			StringBuilder sb = new StringBuilder();			
			sb.append(nodes[nodeOrder[i.next()]].toString());
			while(i.hasNext()) {
				sb.append(", ");
				sb.append(nodes[nodeOrder[i.next()]].toString());
			}
			return sb.toString();
		}
	}
	
	public static class NoGoods {
		protected HashMap<Integer, Collection<NoGood>> node2nogoods = new HashMap<Integer, Collection<NoGood>>();
		protected Earlier earlierRelation = new Earlier();
		
		public void add(int nodeIdx, NoGood nogood) {
			Collection<NoGood> v = node2nogoods.get(nodeIdx);
			if(v == null) {
				// use a tree set with the earlier relation to make sure the
				// constraints are ordered correctly (earliest first).
				// We don't actually require a set, because equal elements are never
				// added, but there seems to be no standard data structure for this. 
				v = new TreeSet<NoGood>(earlierRelation);
				node2nogoods.put(nodeIdx, v);
			}
			v.add(nogood);
		}
		
		/**
		 * returns a collection of constraints for the given node index. The collection is
		 * ordered according to the earlier relation. 
		 * @param nodeIdx
		 * @return
		 */
		public Collection<NoGood> get(int nodeIdx) {
			return node2nogoods.get(nodeIdx);
		}
		
		/**
		 * defines the earlier relation for constraints/nogoods:
		 * The original definition of this relation is as follows:
		 * A constraint C1 with scope S1 is said to be earlier than a constraint C2 with scope S2
		 * if the largest-order variable in S1-S2 has a lower order than the largest-order variable
		 * in S2-S1.
		 * For the case where S1=S2, this implementation arbitrarily selects C1 as earlier.
		 */
		public static final class Earlier implements Comparator<NoGood> {
			@Override
			public int compare(NoGood c1, NoGood c2) {
				final int c1_earlier = -1;
				final int c2_earlier = 1;				
				// look for the largest number that appears in one domain but not the other
				// based on the reverse-order iterators
				Collection<Integer> s1 = c1.getDomain();
				Collection<Integer> s2 = c2.getDomain();
				Iterator<Integer> i1 = s1.iterator();
				Iterator<Integer> i2 = s2.iterator();
				Integer relevant1 = null, relevant2 = null;
				while(true) {
					// if either list has no further elements, we are done 
					if(!i1.hasNext() || !i2.hasNext())
						break;
					// get the next two elements
					int n1 = i1.next();
					int n2 = i2.next();
					// if they are the same, proceed to the next elements
					if(n1 == n2)
						continue;
					// otherwise, we now have one of the relevant numbers and only need to determine the second
					if(n1 < n2) {
						// since n2 was not found in c1, n2 is c2's relevant number
						if(relevant2 == null) { 
							relevant2 = n2;
							if(relevant1 != null)
								break;
						}
						// at this point, relevant1 must be null
						assert relevant1 == null;
						// look for n1 in c2
						while(n1 < n2) {
							if(!i2.hasNext()) 
								// cannot reach a number as low as c1's, so c1 is a relevant number
								// for c1 that is not found in c2, so c1 is definitely earlier 
								return c1_earlier;
							n2 = i2.next();
						}
						if(n2 < n1) { 
							// n1 was not found in c2, so n1 is c1's relevant number, which is definitely
							// smaller than c2's relevant number, so c1 is earlier
							return c1_earlier;
						}
						// otherwise (n1 == n2), continue
					}
					else { // n2 < n1 (analogous to the above)
						if(relevant1 == null) { 
							relevant1 = n1;
							if(relevant2 != null)
								break;
						}
						assert relevant2 == null;
						while(n2 < n1) {
							if(!i1.hasNext()) 
								return c2_earlier;
							n1 = i1.next();
						}
						if(n1 < n2) { 
							return c2_earlier;
						}
					}
				}
				// we have all the information...
				if(relevant1 == null) {
					if(relevant2 == null) // no relevant values, so decide based on scope size
						return s1.size() > s2.size() ? c1_earlier : c2_earlier;
					else // c2's scope is a superset of c1's
						return c2_earlier;						
				}
				else {
					if(relevant2 == null) // c1's scope is a superset of c2's
						return c1_earlier;
					else
						return relevant1 < relevant2 ? c1_earlier : c2_earlier;
				}					
			}
		}
		
		public boolean earlier(NoGood n1, NoGood n2) {
			return earlierRelation.compare(n1, n2) <= 0;
		}
	}
	
	@Override
	public void _infer() throws ProbCogException {
		super._infer();
		report(String.format("#no-goods: %s; max. size: %d; avg. size: %f; total node checks: %d", numNoGoods, maxNoGoodSize, (float)totalNoGoodSize/numNoGoods, numNoGoodNodeChecks));
	}
	
	@Override
	protected void info(int step) {
		System.out.printf("  step %d: %d no-goods recorded\n", step, this.numNoGoods);
	}

	@Override
	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws ProbCogException {
		s.trials = 1;
		s.operations = 0;
		s.weight = 1.0;

		HashMap<Integer,PrioritySet<Integer>> backjumpSets = new HashMap<Integer,PrioritySet<Integer>>();
		boolean backtracking = false;
		DomainExclusions domExclusions = new DomainExclusions();		
		
		for(int i = 0; i < evidenceDomainIndices.length; i++)
			s.nodeDomainIndices[i] = evidenceDomainIndices[i];

		// assign values to the nodes in order
		for(int orderIdx = 0; orderIdx < nodeOrder.length;) {
			s.operations++;
			int nodeIdx = nodeOrder[orderIdx];
			boolean valueSuccessfullyAssigned = false;
			
			if(!backtracking) {				
				// if we get to a node going forward, any previous exclusions are obsolete
				// and the backjump set can be reset
				domExclusions.remove(nodeIdx);
				backjumpSets.remove(orderIdx);
				
				if(debug) System.out.println("    Op" + s.operations + ": #" + node2orderIndex.get(nodes[nodeIdx]) + " " + nodes[nodeIdx].getName() + ", current setting: " + s.nodeDomainIndices[nodeIdx]);
			}
			else {
				if(debug) System.out.println("    Op" + s.operations + ": backtracking to #" + node2orderIndex.get(nodes[nodeIdx]) + " " + nodes[nodeIdx].getName() + ", current setting: " + s.nodeDomainIndices[nodeIdx]);
				
				domExclusions.add(nodeIdx, s.nodeDomainIndices[nodeIdx]);
			}
			
			if(!debug && infoInterval == 1)
				System.out.printf("#%d, %d nogoods\r", nodeIdx, numNoGoods);
			
			int domainIdx = evidenceDomainIndices[nodeIdx];
			
			PrioritySet<Integer> backjumpSet = backjumpSets.get(orderIdx);
			if(backjumpSet == null)
				backjumpSet = new PrioritySet<Integer>(new PriorityQueue<Integer>(2, highestFirst));
			
			// for evidence nodes, we can continue if the evidence
			// probability was non-zero
			if(domainIdx >= 0) {
				s.nodeDomainIndices[nodeIdx] = domainIdx;
				samplingProb[nodeIdx] = 1.0;
				double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
				if(prob != 0.0)
					valueSuccessfullyAssigned = true;
				else {
					// the minimal conflict set is given by the non-evidence parents,
					// so add them to the backjump set
					BeliefNode[] domProd = nodes[nodeIdx].getCPF().getDomainProduct();
					for(int k = 1; k < domProd.length; k++) {
						int parentNodeIdx = getNodeIndex(domProd[k]);
						if(evidenceDomainIndices[parentNodeIdx] == -1) {
							Integer parentOrderIdx = node2orderIndex.get(domProd[k]);
							backjumpSet.add(parentOrderIdx);
						}
					}
				}
			}
			
			// for non-evidence nodes, do forward sampling
			else {
				// get domain exclusions
				boolean[] excluded = domExclusions.get(nodeIdx);
				
				// get conditional distribution that applies to the current parent configuration and
				// determine the domain indices for which the parents constitute a nogood
				double[] dist = getConditionalDistribution(nodes[nodeIdx], s.nodeDomainIndices);
				NoGood parentNoGood = null;
				NoGood[] parentNoGoods = new NoGood[dist.length];
				for(int i = 0; i < dist.length; i++) {
					if(dist[i] == 0.0) {
						if(parentNoGood == null) {
							parentNoGood = new NoGood(-1);
							BeliefNode[] domProd = nodes[nodeIdx].getCPF().getDomainProduct();
							for(int k = 1; k < domProd.length; k++) {
								int parentNodeIdx = getNodeIndex(domProd[k]);
								if(evidenceDomainIndices[parentNodeIdx] == -1)
									parentNoGood.addSetting(parentNodeIdx, s.nodeDomainIndices[parentNodeIdx]);
							}
						}
						parentNoGoods[i] = parentNoGood;
					}
					else {
						parentNoGoods[i] = null;
						if(excluded[i])
							dist[i] = 0;
					}
				}									
				
				// add additional exclusions based on no-goods
				NoGood[] earliest = new NoGood[dist.length];
				if(useNoGoods) {
					Collection<NoGood> v = noGoods.get(nodeIdx);
					if(v != null) {
						//if(debug) System.out.println("      checking " + v.size() + " nogoods: " + StringTool.join(", ", v));
						for(NoGood ng : v) {
							int domIdx = ng.domIdx;
							if(!excluded[domIdx] && earliest[domIdx] == null) {
								// if there is a parent nogood for this domain index, check if the current
								// nogood is earlier
								boolean checkNoGood = true;
								if(parentNoGoods[domIdx] != null) 
									checkNoGood = noGoods.earlier(ng, parentNoGoods[domIdx]);								
								if(checkNoGood && ng.isApplicable(s.nodeDomainIndices)) {
									if(debug) {
										s.nodeDomainIndices[nodeIdx] = ng.domIdx;
										boolean OK1 = verifyNoGoodInContext(ng, s.nodeDomainIndices);
										boolean OK2 = verifyNoGood(this.nodes[nodeIdx], ng);  
										if(!OK1 || !OK2)
											throw new ProbCogException("nogood is bad");
									}
									earliest[domIdx] = ng;	
									dist[domIdx] = 0;
									if(debug) System.out.printf("      nogood excluded %d (%s): %s\n", ng.domIdx, nodes[nodeIdx].getDomain().getName(ng.domIdx), ng.toString());
								}
							}
						}
					}
				}
				
				// sample
				SampledAssignment sa; 
				sa = sample(dist);
				//sa = sampleForward(nodes[nodeIdx], s.nodeDomainIndices, excluded);
				if(sa != null) {
					domainIdx = sa.domIdx;
					samplingProb[nodeIdx] = sa.probability;		
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					valueSuccessfullyAssigned = true;
				}
				else {
					// extend the backjump set for all the constraints that applied
					for(int i = 0; i < dist.length; i++) {
						NoGood ng = earliest[i];
						if(ng == null)
							ng = parentNoGoods[i];
						if(ng != null) {
							if(debug) System.out.println("      mce constraint for value " + i + " on: " + ng.getDomainString() + (ng == parentNoGoods[i] ? " (parents)" : ""));
							for(Integer o : ng.getDomain()) {
								backjumpSet.add(o);
							}
						}
					}
				}
			}
			
			// debug info
			if(debug) {
				//System.out.printf("    step %d, node #%d '%s' (%d/%d exclusions)  ", currentStep, node2orderIndex.get(nodes[nodeIdx]), nodes[nodeIdx].getName(), numex, excluded.length);*/
				if(evidenceDomainIndices[nodeIdx] == -1) {
					if(valueSuccessfullyAssigned) {
						Domain dom = nodes[nodeIdx].getDomain();
						System.out.printf("      assigned %d (%s), (%d/%d) exclusions\n", domainIdx, dom.getName(domainIdx), domExclusions.getNumExclusions(nodeIdx), dom.getOrder());
					}
					else
						System.out.println("      out of choices; backtracking...");
				}
				else {
					if(valueSuccessfullyAssigned)
						System.out.printf("      evidence %d (%s) OK\n", domainIdx, nodes[nodeIdx].getDomain().getName(domainIdx));
					else
						System.out.printf("      evidence %d (%s) with probability 0.0; backtracking... cond: %s\n", domainIdx, nodes[nodeIdx].getDomain().getName(domainIdx), s.getCPDLookupString(nodes[nodeIdx]));
				}
			}
			
			// if a value was successfully assigned, continue to the next node in the order
			if(valueSuccessfullyAssigned) {
				backtracking = false;				
				++orderIdx;
			}
			// otherwise, jump back and record a constraint/nogood
			else {
				s.trials++;
				backtracking = true;
				
				// back jump				
				if(backjumpSet.isEmpty())
					throw new ProbCogException("Nowhere left to backjump to from node #" + orderIdx + ". Most likely, the evidence has 0 probability.");
				orderIdx = backjumpSet.remove();
				
				// record nogood
				NoGood ng = new NoGood(s.nodeDomainIndices[nodeOrder[orderIdx]]);
				for(Integer oIdx : backjumpSet)
					ng.addSettingOrderIdx(oIdx, s.nodeDomainIndices[nodeOrder[oIdx]]);
				noGoods.add(nodeOrder[orderIdx], ng);	
				if(debug) System.out.println("      recorded nogood for " + nodes[nodeOrder[orderIdx]] + ": " + ng);
				++numNoGoods;
				
				// merge to update the new node's backjump set 
				PrioritySet<Integer> oldQueue = backjumpSets.get(orderIdx);
				if(oldQueue == null) {
					oldQueue = new PrioritySet<Integer>(new PriorityQueue<Integer>(1, highestFirst));
					backjumpSets.put(orderIdx, oldQueue);
				}
				for(Integer j : backjumpSet)
					oldQueue.add(j);

				// reset assignment
				s.nodeDomainIndices[nodeIdx] = evidenceDomainIndices[nodeIdx];
			}
		}
		
		//System.out.printf("    no-goods: %d, trials: %d\n", this.numNoGoods, s.trials);

		return s;
	}
	
	protected SampledAssignment sample(double[] dist) {
		double sum = 0;
		for(int i = 0; i < dist.length; i++)
			sum += dist[i];
		// if the distribution contains only zeros, it is an impossible case -> cannot sample
		if(sum == 0)
			return null;
		int domIdx = sample(dist, sum, generator);
		return new SampledAssignment(domIdx, dist[domIdx]/sum);
	}
	
	/**
	 * verifies that the given nogood is indeed correct by running SampleSearch to find 
	 * a sample  
	 * @param ng the nogood to check
	 * @return whether the nogood is OK
	 */
	protected boolean verifyNoGood(BeliefNode n, NoGood ng) throws ProbCogException {
		if(!verifyNoGoods)
			return true;
		if(verifiedNoGoods == null) 
			verifiedNoGoods = new HashSet<NoGood>();
		if(verifiedNoGoods.contains(ng))
			return true;
		SampleSearch ss = new SampleSearch(this.bn);
		int[] nodeDomainIndices = evidenceDomainIndices.clone();
		nodeDomainIndices[getNodeIndex(n)] = ng.domIdx;
		for(Pair<Integer,Integer> e : ng.nodeSettings.values()) {
			nodeDomainIndices[e.first] = e.second;
		}
		ss.setEvidence(nodeDomainIndices);
		ss.setNumSamples(1);
		ss.setVerbose(false);
		System.out.print("      verifying " + ng + "... ");
		boolean haveSample = true;
		try {
			ss.infer();
		}
		catch(Exception e) {
			haveSample = false;
		}
		System.out.println(!haveSample ? "OK" : "BAD!");
		verifiedNoGoods.add(ng);
		return !haveSample; 
	}
	
	protected boolean verifyNoGoodInContext(NoGood ng, int[] nodeDomainIndices) throws ProbCogException {
		if(!verifyNoGoods)
			return true;
		SampleSearch ss = new SampleSearch(this.bn);
		ss.setEvidence(nodeDomainIndices);
		ss.setNumSamples(1);
		ss.setVerbose(false);
		System.out.print("      verifying " + ng + " in current context... ");
		boolean haveSample = true;
		try {
			ss.infer();
		}
		catch(Exception e) {
			haveSample = false;
		}
		System.out.println(!haveSample ? "OK" : "BAD!");
		return !haveSample; 
	}
}

