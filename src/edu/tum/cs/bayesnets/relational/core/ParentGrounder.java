package edu.tum.cs.bayesnets.relational.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;

import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork.RelationKey;
import edu.tum.cs.bayesnets.relational.core.RelationalNode.Signature;
import edu.tum.cs.bayesnets.relational.learning.Database;

public class ParentGrounder {
	protected class FunctionalLookup {
		protected RelationKey key;
		protected RelationalNode node;
		
		public FunctionalLookup(RelationKey key, RelationalNode node) {
			this.key = key;
			this.node = node;
		}
		
		public boolean doLookup(Database db, HashMap<String,String> varBindings) {
			String[] keyValues = new String[key.keyIndices.size()];
			int i = 0;
			for(Integer idxParam : key.keyIndices)
				keyValues[i++] = varBindings.get(node.params[idxParam]);			
			String[] params = db.getParameterSet(this.key, keyValues);
			if(params == null) {
				String[] buf = new String[node.params.length];
				for(int k = 0; k < node.params.length; k++) buf[k] = "_";
				int j = 0;
				for(Integer k : key.keyIndices) buf[k] = keyValues[j++];
				System.err.println("Could not perform lookup for " + RelationalNode.formatName(node.getName(), buf));
				return false;
			}
			// update the variable bindings
			java.util.Iterator<Integer> iter = key.keyIndices.iterator();
			int nextKey = iter.next();
			for(i = 0; i < node.params.length; i++) {
				if(i == nextKey) {
					nextKey = iter.hasNext() ? iter.next() : -1;
					continue;
				}
				varBindings.put(node.params[i], params[i]);
			}
			return true;
		}
	}	
	
	protected Vector<FunctionalLookup> functionalLookups;
	protected RelationalNode mainNode;
	protected RelationalNode[] parents;
	
	public ParentGrounder(RelationalBeliefNetwork bn, RelationalNode child) throws Exception {
		/**
		 * variable lookup
		 */
		functionalLookups = new Vector<FunctionalLookup>();
		mainNode = child;
		
		HashSet<String> handledVars = new HashSet<String>();
		for(String p : mainNode.params) 
			handledVars.add(p);
		
		parents = bn.getRelationalParents(child);
		ParentGrounder[] argmap = new ParentGrounder[parents.length];
		Collection<RelationalNode> workingSet = new LinkedList<RelationalNode>();
		for(RelationalNode p : parents) workingSet.add(p);
		while(!workingSet.isEmpty()) { // while we have parents that aren't yet grounded...
			Collection<RelationalNode> newWorkingSet = new LinkedList<RelationalNode>();
			// go through all of them
			int gains = 0;
			for(RelationalNode n : workingSet) {
				int numHandledParams = 0;
				FunctionalLookup flookup = null;
				Signature s = bn.getSignature(n);
				// check all of the parent's parameters
				for(String param : n.params) {					
					if(!handledVars.contains(param)) {
						// check if we can handle this parameter via a functional lookup
						// - if we already have a functional lookup for this node, we definitely can
						if(flookup != null) {
							++numHandledParams;
							++gains;
						}
						// - otherwise look at all the keys for the relation and check if we 
						//   already have one of them completely
						else {
							Collection<RelationKey> keys = bn.getRelationKeys(n.getName());
							if(keys != null) {
								for(RelationKey key : keys) {
									int c = 0;
									for(Integer keyParamIdx : key.keyIndices) {
										if(handledVars.contains(n.params[keyParamIdx]))
											++c;
									}
									if(c == key.keyIndices.size()) {
										handledVars.add(param);
										flookup = new FunctionalLookup(key, n);
										functionalLookups.add(flookup);
										++numHandledParams;
										++gains;
										break;
									}										
								}
							}
						}
					}
					else
						++numHandledParams;
				}
				// if not all the parameters were handled, we need to reiterate
				if(numHandledParams != n.params.length)
					newWorkingSet.add(n);
			}
			// if there weren't any gains in this iteration, then we cannot ground the parents
			if(gains == 0 && !newWorkingSet.isEmpty()) {
				throw new Exception("Could not determine how to ground parents of " + mainNode + "; some parameters of " + newWorkingSet + " could not be resolved.");
			}
			workingSet = newWorkingSet;
		}
	}
	
	/**
	 * generates a grounding of all parent nodes (and the main node itself) given a vector of actual parameters for this object's main node 
	 * @param actualParameters actual parameters of the man node for which this parameter grounding should be performed
	 * @return mapping of node indices to vectors of corresponding actual parameters
	 */
	public Map<Integer, String[]> generateParameterSets(String[] actualParameters, Database db) {
		HashMap<Integer, String[]> m = new HashMap<Integer, String[]>();			
		m.put(this.mainNode.index, actualParameters);
		HashMap<String, String> varBindings = generateVarBindings(actualParameters, db);
		if(varBindings != null) {
			for(RelationalNode parent : this.parents) {
				String[] params = new String[parent.params.length];
				for(int i = 0; i < params.length; i++)
					params[i] = varBindings.get(parent.params[i]);
				m.put(parent.index, params);
			}
			return m;
		}
		else
			return null;
	}
	
	protected HashMap<String, String> generateVarBindings(String[] actualParameters, Database db) {
		HashMap<String, String> bindings = new HashMap<String, String>();
		// add known bindings from main node 
		for(int i = 0; i < mainNode.params.length; i++)
			bindings.put(mainNode.params[i], actualParameters[i]);
		// perform functional lookups
		for(FunctionalLookup fl : this.functionalLookups)
			if(!fl.doLookup(db, bindings))
				return null;
		return bindings;
	}
}