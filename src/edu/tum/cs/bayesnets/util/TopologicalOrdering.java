package edu.tum.cs.bayesnets.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

public class TopologicalOrdering implements Iterable<Integer>, Iterator<Integer> {
	
	Vector<Vector<Integer>> partialOrder;
	int idxCurrentLevel;
	Vector<Integer> currentLevel;
	HashMap<BeliefNode, Integer> tierMap;
	Random random;
	
	public TopologicalOrdering(Vector<Vector<Integer>> partialOrder, HashMap<BeliefNode, Integer> tierMap, Random rand) {
		this.partialOrder = partialOrder;
		this.tierMap = tierMap;
		this.random = rand; 
		reset();
	}
	
	public TopologicalOrdering(Vector<Vector<Integer>> partialOrder, HashMap<BeliefNode, Integer> tierMap) {
		this(partialOrder, tierMap, new Random());
	}
	
	public void reset() {
		idxCurrentLevel = 0;
		currentLevel = new Vector<Integer>(partialOrder.get(0));
	}

	public Iterator<Integer> iterator() {
		return this;
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
			if(idxCurrentLevel < partialOrder.size())
				currentLevel = new Vector<Integer>(partialOrder.get(idxCurrentLevel));
			else
				currentLevel = null;
		}
		return nextElem;
	}
	
	public int getTier(BeliefNode n) {
		if(tierMap == null)
			throw new RuntimeException("Topological ordering has no tier map!");
		return tierMap.get(n);
	}

	public void remove() {
		throw new RuntimeException("remove() is not supported by this constant iterator.");
	}
}
