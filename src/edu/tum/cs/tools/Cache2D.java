/**
 * 
 */
package edu.tum.cs.tools;

import java.util.HashMap;

public class Cache2D<TContext, TKey, TValue> {
	protected HashMap<TContext, HashMap<TKey, TValue>> cache;
	protected HashMap<TKey, TValue> subcache;
	protected TKey key;
	public int numCacheHit = 0;
	public int numCacheMiss = 0;
	
	public Cache2D() {
		cache = new HashMap<TContext, HashMap<TKey, TValue>>();			
	}
	
	public TValue get(TContext context, TKey key) {
		this.key = key;
		subcache = cache.get(context);
		if(subcache == null) {
			subcache = new HashMap<TKey, TValue>();
			cache.put(context, subcache);
			numCacheMiss++;
			return null;
		}
		
		TValue value = subcache.get(key);
		if(value == null)
			numCacheMiss++;
		else 
			numCacheHit++;
		return value;
	}
	
	public void put(TValue value) {
		subcache.put(key, value);
	}
	
	public float getHitRatio() {
		return (float)numCacheHit/getNumAccesses();
	}
	
	public int getNumAccesses() {
		return numCacheHit+numCacheMiss;
	}
}