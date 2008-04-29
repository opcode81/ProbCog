package edu.tum.cs.bayesnets.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Vector;

public class TopologicalOrdering implements Iterable<Integer>, Iterator<Integer> {
	
	Vector<Vector<Integer>> partialOrder;
	int idxCurrentLevel;
	Vector<Integer> currentLevel;
	Random random;
	
	public TopologicalOrdering(Vector<Vector<Integer>> partialOrder, Random rand) {
		this.partialOrder = partialOrder;
		this.random = rand; 
		reset();
	}
	
	public TopologicalOrdering(Vector<Vector<Integer>> partialOrder) {
		this(partialOrder, new Random());
	}
	
	public void reset() {
		idxCurrentLevel = 0;
		currentLevel = new Vector<Integer>(partialOrder.get(0));
	}

	public Iterator<Integer> iterator() {
		return this;
	}

	public boolean hasNext() {
		return !currentLevel.isEmpty();
	}

	public Integer next() {
		if(!hasNext())
			throw new NoSuchElementException();
		int idxNext = random.nextInt(currentLevel.size());
		int nextElem = currentLevel.remove(idxNext);
		if(currentLevel.isEmpty()) {
			currentLevel = new Vector<Integer>(partialOrder.get(++idxCurrentLevel));
		}
		return nextElem;
	}

	public void remove() {
		throw new RuntimeException("remove() is not supported by this constant iterator.");
	}
}
