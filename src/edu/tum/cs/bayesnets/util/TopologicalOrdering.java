package edu.tum.cs.bayesnets.util;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

public class TopologicalOrdering implements Iterable<Integer> {
	/**
	 * a vector of tiers reflecting the partial ordering of the nodes; the nodes in each tier (sub-vector) are equivalent with respect to the ordering
	 */
	Vector<Vector<Integer>> partialOrder;
	/**
	 * a mapping from belief nodes to 0-based tier indices (i.e. depth in the ordering)
	 */
	HashMap<BeliefNode, Integer> tierMap;
	
	public TopologicalOrdering(Vector<Vector<Integer>> partialOrder, HashMap<BeliefNode, Integer> tierMap) {
		this.partialOrder = partialOrder;
		this.tierMap = tierMap;
	}

	public java.util.Iterator<Integer> iterator() {
		return new Iterator(this);
	}

	public static class Iterator implements java.util.Iterator<Integer> {

		int idxCurrentLevel;
		Vector<Integer> currentLevel;
		Random random;
		TopologicalOrdering ordering;
		
		public Iterator(TopologicalOrdering ordering) {
			this.ordering = ordering;
			idxCurrentLevel = 0;
			currentLevel = new Vector<Integer>(ordering.partialOrder.get(0));
			random = new Random();
		}
		
		public boolean hasNext() {
			return currentLevel != null && !currentLevel.isEmpty();
		}
	
		public Integer next() {
			if(!hasNext())
				throw new NoSuchElementException();
			int idxNext = random.nextInt(currentLevel.size());
			int nextElem = currentLevel.remove(idxNext);
			if(currentLevel.isEmpty()) {
				++idxCurrentLevel;
				if(idxCurrentLevel < ordering.partialOrder.size())
					currentLevel = new Vector<Integer>(ordering.partialOrder.get(idxCurrentLevel));
				else
					currentLevel = null;
			}
			return nextElem;
		}
		
		public void remove() {
			throw new RuntimeException("remove() is not supported by this constant iterator.");
		}

	}

	/**
	 * gets the tier (i.e. depth in the ordering) of the given node
	 * @param n a belief node
	 * @return 0-based tier
	 */
	public int getTier(BeliefNode n) {
		if(tierMap == null)
			throw new RuntimeException("Topological ordering has no tier map!");
		return tierMap.get(n);
	}

	public int getNumTiers() {
		return partialOrder.size();
	}
	
	/**
	 * gets the vector of indices of nodes belonging to the given tier
	 * @param index
	 * @return
	 */
	public Vector<Integer> getTier(int index) {
		return partialOrder.get(index);
	}
	
	public Vector<Vector<Integer>> getTiers() {
		return partialOrder;
	}
}
