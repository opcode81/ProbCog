package edu.tum.cs.bayesnets.relational.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
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
			// build the key values
			String[] keyValues = new String[key.keyIndices.size()];
			int i = 0;
			for(Integer idxParam : key.keyIndices)
				keyValues[i++] = varBindings.get(node.params[idxParam]);	
			// perform the lookup
			String[] params = db.getParameterSet(this.key, keyValues);
			if(params == null) { // lookup yielded no values
				String[] buf = new String[node.params.length];
				for(int k = 0; k < node.params.length; k++) buf[k] = "_";
				int j = 0;
				for(Integer k : key.keyIndices) buf[k] = keyValues[j++];
				System.err.println("Could not perform lookup for " + RelationalNode.formatName(node.getFunctionName(), buf));
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
	/**
	 * parameters that cannot be uniquely determined, i.e. parameters for which we consider all possible bindings
	 */
	protected String[] ungroundedParams;
	protected String[] ungroundedParamDomains;
	
	public ParentGrounder(RelationalBeliefNetwork bn, RelationalNode child) throws Exception {
		functionalLookups = new Vector<FunctionalLookup>();
		mainNode = child;
		parents = bn.getRelationalParents(child);
		ungroundedParams = child.addParams;
		
		// keep a list of variables which we know how to ground
		HashSet<String> handledVars = new HashSet<String>();
		// - all parameters of the main node are obviously known
		for(String p : mainNode.params) 
			handledVars.add(p);
		// - additional parameters of the main node, for which we must generate all possible bindings, can be regarded as handled
		if(mainNode.addParams != null)
			for(String p : mainNode.addParams)
				handledVars.add(p);
	
		// determine domains of ungrounded params (if any)
		if(ungroundedParams != null) {
			ungroundedParamDomains = new String[ungroundedParams.length];
			for(int i = 0; i < ungroundedParams.length; i++) {
				for(RelationalNode parent : parents) {
					for(int j = 0; j < parent.params.length; j++) {					
						if(parent.params[j].equals(ungroundedParams[i])) {
							Signature sig = parent.getSignature();
							if(sig != null && !parent.isConstant) { // sig can be null for built-in predicates
								if(sig.argTypes.length != parent.params.length)
									throw new Exception(String.format("Parameter count in signature %s (%d) does not match node %s (%d).", sig.toString(), sig.argTypes.length, parent.toString(), parent.params.length));
								ungroundedParamDomains[i] = sig.argTypes[j];
							}
						}
					}
				}
			}
		}
		
		// now take care of the yet unhandled parameters
		Collection<RelationalNode> workingSet = new LinkedList<RelationalNode>();
		for(RelationalNode p : parents) 
			workingSet.add(p);
		// while we have parents that aren't yet grounded...
		while(!workingSet.isEmpty()) { 
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
							Collection<RelationKey> keys = bn.getRelationKeys(n.getFunctionName());
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
	 * generates a grounding of all parent nodes (and the main node itself), i.e. a list of actual parameters for each node, given a vector of actual parameters for this object's main node 
	 * @param actualParameters actual parameters of the man node for which this parameter grounding should be performed
	 * @return mapping of node indices to lists of corresponding actual parameters
	 */
	public Map<Integer, String[]> generateParameterSets(String[] actualParameters, Database db) {
		HashMap<Integer, String[]> m = new HashMap<Integer, String[]>();			
		m.put(this.mainNode.index, actualParameters);
		// generate the variable bindings via parameter matching and functional lookups
		HashMap<String, String> varBindings = generateParameterBindings(actualParameters, db);
		// get the parameter set for each parent node
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
	
	/**
	 * generates all possible groundings of all parent nodes (and the main node itself), where a grounding is a list of actual parameters for each node, given a vector of actual parameters for this object's main node 
	 * @param actualParameters actual parameters of the main node for which this parameter grounding should be performed
	 * @return vector of mappings of node indices to lists of corresponding actual parameters
	 * @throws Exception 
	 */
	public Vector<Map<Integer, String[]>> getGroundings(String[] actualParameters, Database db) throws Exception {
		// generate all the parameter bindings we can
		HashMap<String, String> paramBindings = generateParameterBindings(actualParameters, db);
		if(paramBindings == null)
			return null;
		// complete the bindings and get the parameter sets for each complete binding
		Vector<Map<Integer, String[]>> v = new Vector<Map<Integer, String[]>>();
		getCompleteGroundings(actualParameters, db, paramBindings, 0, v);		
		return v;
	}
	
	/**
	 * completes a (yet incomplete grounding) grounding by considering all possibilities of grounding the ungrounded variables
	 * @param mainNodeParams
	 * @param db
	 * @param paramBindings
	 * @param idx
	 * @param ret
	 * @throws Exception 
	 */
	protected void getCompleteGroundings(String[] mainNodeParams, Database db, HashMap<String, String> paramBindings, int idx, Vector<Map<Integer, String[]>> ret) throws Exception {
		if(ungroundedParams == null || idx == ungroundedParams.length) {
			// all variables have been grounded, so now generate a mapping: node index -> list of actual parameters
			HashMap<Integer, String[]> m = new HashMap<Integer, String[]>();
			m.put(this.mainNode.index, mainNodeParams);
			for(RelationalNode parent : this.parents) {
				String[] params = new String[parent.params.length];
				for(int i = 0; i < params.length; i++)
					params[i] = paramBindings.get(parent.params[i]);
				m.put(parent.index, params);
			}
			ret.add(m);
		}
		else { // ground the next variable
			String param = ungroundedParams[idx];
			Set<String> s = db.getDomain(ungroundedParamDomains[idx]);
			if(s == null) 
				throw new Exception("Domain " + ungroundedParamDomains[idx] + " not found!");			
			for(String constant : s) {
				paramBindings.put(param, constant);
				getCompleteGroundings(mainNodeParams, db, paramBindings, idx+1, ret);
			}
		}
	}
	
	protected HashMap<String, String> generateParameterBindings(String[] actualParameters, Database db) {
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