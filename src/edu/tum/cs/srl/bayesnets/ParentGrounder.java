package edu.tum.cs.srl.bayesnets;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.GenericDatabase;
import edu.tum.cs.srl.RelationKey;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.taxonomy.Taxonomy;
import edu.tum.cs.util.StringTool;

/**
 * generates groundings of all nodes relevant to a given node N for particular bindings of N's parameters; the relevant nodes being N and its parents  
 * @author jain
 */
public class ParentGrounder {
	
	protected class FunctionalLookup {
		protected RelationKey key;
		protected RelationalNode node;
		
		public FunctionalLookup(RelationKey key, RelationalNode node) {
			this.key = key;
			this.node = node;
		}
		
		/**
		 * perform the functional lookup, extending the given variable binding with the result
		 * @param db
		 * @param varBindings the variable binding to extend with the results of the lookup
		 * @throws Exception if a lookup that is required to work failed
		 * @return true if the lookup could be performed to extend the variable binding,
		 * false if the lookup is not applicable because the precondition is not met
		 */
		public boolean doLookup(GenericDatabase<?,?> db, HashMap<String,String> varBindings) throws Exception {
			// build the key values
			String[] keyValues = new String[key.keyIndices.size()];
			int i = 0;
			for(Integer idxParam : key.keyIndices)
				keyValues[i++] = varBindings.get(node.params[idxParam]);	
			// perform the lookup			
			String[] params = db.getParameterSet(this.key, keyValues);
			if(params == null) { // lookup yielded no values
				// if the node is a precondition, we report that the lookup failed, indicating that
				// there is no valid instantiation of all variables
				if(this.node.isPrecondition)  
					return false;
				// if the lookup failed but does not refer to a required precondition,
				// we have an error case
				String[] buf = new String[node.params.length];
				for(int k = 0; k < node.params.length; k++) buf[k] = "_";
				int j = 0;
				for(Integer k : key.keyIndices) buf[k] = keyValues[j++];
				throw new Exception("Could not perform required lookup for " + Signature.formatVarName(node.getFunctionName(), buf));
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
	protected Collection<RelationalNode> parents;
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
		// If a parameter appears in more than one parent, we use the most specific type in the taxonomy that we come across
		Taxonomy taxonomy = bn.getTaxonomy();
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
								if(ungroundedParamDomains[i] == null || taxonomy.query_isa(sig.argTypes[j], ungroundedParamDomains[i]))
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
					//System.out.println("trying to handle " + param + " in " + n);
					if(!handledVars.contains(param) && !RelationalNode.isConstant(param)) {
						// check if we can handle this parameter via a functional lookup
						// - if we already have a functional lookup for this node, we definitely can
						if(flookup != null) {
							handledVars.add(param);
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
										//System.out.println(key + " can handle " + param);
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
				throw new Exception("Could not determine how to ground parents of " + mainNode + "; some parameters of " + newWorkingSet + " could not be resolved; handled vars: " + handledVars);
			}
			workingSet = newWorkingSet;
		}
	}
	
	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer("<known from main node: ");
		// known bindings from main node 
		ret.append(StringTool.join(", ", mainNode.params));
		// functional lookups
		ret.append("; functional lookups: ");
		int i = 0; 
		for(FunctionalLookup fl : this.functionalLookups) {
			if(i++ > 0)
				ret.append(", ");
			ret.append(fl.key.toString());
		}
		ret.append(">");
		return ret.toString();
	}
	
	/**
	 * generates a grounding of all parent nodes (and the main node itself), i.e. a list of actual parameters for each node, given a vector of actual parameters for this object's main node 
	 * @param actualParameters actual parameters of the man node for which this parameter grounding should be performed
	 * @return mapping of node indices to lists of corresponding actual parameters or null
	 * @throws Exception 
	 * @deprecated this method is apparently not required; it is not referenced anywhere
	 */
	public Map<Integer, String[]> generateParameterSets(String[] actualParameters, Database db) throws Exception {
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
	 * @return vector of mappings of node indices to lists of corresponding actual parameters or null if there is no valid binding for the given actual parameters of the main node
	 * @throws Exception 
	 */
	public Vector<Map<Integer, String[]>> getGroundings(String[] actualParameters, GenericDatabase<?,?> db) throws Exception {
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
	 * completes a (yet incomplete) grounding by considering all possibilities of grounding the ungrounded variables
	 * @param mainNodeParams
	 * @param db
	 * @param paramBindings
	 * @param idx
	 * @param ret
	 * @throws Exception 
	 */
	protected void getCompleteGroundings(String[] mainNodeParams, GenericDatabase<?,?> db, HashMap<String, String> paramBindings, int idx, Vector<Map<Integer, String[]>> ret) throws Exception {
		if(ungroundedParams == null || idx == ungroundedParams.length) {
			// all variables have been grounded, so now generate a mapping: node index -> list of actual parameters
			HashMap<Integer, String[]> m = new HashMap<Integer, String[]>();
			m.put(this.mainNode.index, mainNodeParams);
			for(RelationalNode parent : this.parents) {
				String[] params = new String[parent.params.length];
				for(int i = 0; i < params.length; i++) {
					if(RelationalNode.isConstant(parent.params[i]))
						params[i] = parent.params[i];
					else
						params[i] = paramBindings.get(parent.params[i]);
				}
				m.put(parent.index, params);
			}
			ret.add(m);
		}
		else { // ground the next variable
			String param = ungroundedParams[idx];
			Iterable<String> s = db.getDomain(ungroundedParamDomains[idx]);
			if(s == null) 
				throw new Exception("Domain " + ungroundedParamDomains[idx] + " not found!");			
			for(String constant : s) {
				paramBindings.put(param, constant);
				getCompleteGroundings(mainNodeParams, db, paramBindings, idx+1, ret);
			}
		}
	}
	
	/**
	 * generates, for a particular binding of the main node's parameters, the complete binding of all relevant variables (which includes all variables/parameters of parents) using the functional lookups that were determined at construction time 
	 * @param actualParameters
	 * @param db
	 * @return
	 * @throws Exception 
	 */
	public HashMap<String, String> generateParameterBindings(String[] actualParameters, GenericDatabase<?,?> db) throws Exception {
		HashMap<String, String> bindings = new HashMap<String, String>();
		// add known bindings from main node 
		for(int i = 0; i < mainNode.params.length; i++)
			bindings.put(mainNode.params[i], actualParameters[i]);
		// perform functional lookups
		for(FunctionalLookup fl : this.functionalLookups)
			if(!fl.doLookup(db, bindings)) // if a functional lookup cannot be applied, there is no full parameter binding (probably because a precondition is not applicable)
				return null;
		return bindings;
	}
}