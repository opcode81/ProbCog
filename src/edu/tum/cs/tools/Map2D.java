package edu.tum.cs.tools;

import java.util.HashMap;

public class Map2D<TContext, TKey, TValue> {
	/**
	 * the actual two-dimensional mapping
	 */
	protected HashMap<TContext, HashMap<TKey, TValue>> map;
	/**
	 * the last-used submap
	 */
	protected HashMap<TKey, TValue> submap;
	/**
	 * the key in the current submap that was last retrieved
	 */
	protected TKey key;
	
	public Map2D() {
		map = new HashMap<TContext, HashMap<TKey, TValue>>();			
	}
	
	public TValue get(TContext context, TKey key) {
		this.key = key;
		submap = map.get(context);
		if(submap == null) {
			submap = new HashMap<TKey, TValue>();
			map.put(context, submap);
			return null;
		}		
		return submap.get(key);
	}
	
	public HashMap<TKey, TValue> getSubmap(TContext context) {
		return map.get(context);
	}
	
	public void put(TValue value) {
		submap.put(key, value);
	}
	
	public void put(TContext context, TKey key, TValue value) {
		submap = map.get(context);
		if(submap == null) {
			submap = new HashMap<TKey, TValue>();
			map.put(context, submap);			
		}
		submap.put(this.key = key, value);
	}
}