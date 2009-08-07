package edu.tum.cs.tools;

import java.util.HashMap;
import java.util.Vector;

/**
 * represents a mapping from keys to lists of values (specifically java.util.Vectors of values)
 * @author jain
 */
public class Map2List<TKey, TValue> {
	/**
	 * the actual mapping
	 */
	protected HashMap<TKey, Vector<TValue>> map;
	
	public Map2List() {
		map = new HashMap<TKey, Vector<TValue>>();			
	}
	
	/**
	 * gets the list of values stored for the given key
	 * @param key
	 * @return a vector of values or null of no values were saved yet for the given key
	 */
	public Vector<TValue> get(TKey key) {
		return map.get(key);
	}
	
	/**
	 * adds to the list managed for key the given value, creating the list if it doesn't exist yet
	 * @param key
	 * @param value
	 */
	public void add(TKey key, TValue value) {
		Vector<TValue> v = map.get(key);
		if(v == null) {
			v = new Vector<TValue>();
			map.put(key, v);			
		}
		v.add(value);
	}
}