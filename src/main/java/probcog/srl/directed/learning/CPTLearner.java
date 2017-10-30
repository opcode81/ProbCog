/*******************************************************************************
 * Copyright (C) 2007-2012 Dominik Jain.
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
package probcog.srl.directed.learning;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import probcog.exception.ProbCogException;
import probcog.srl.Database;
import probcog.srl.GenericDatabase;
import probcog.srl.Signature;
import probcog.srl.ValueDistribution;
import probcog.srl.directed.DecisionNode;
import probcog.srl.directed.ExtendedNode;
import probcog.srl.directed.ParentGrounder;
import probcog.srl.directed.RelationalBeliefNetwork;
import probcog.srl.directed.RelationalNode;
import probcog.srl.directed.ParentGrounder.ParentGrounding;

import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.ksu.cis.bnj.ver3.core.values.ValueZero;
import edu.tum.cs.util.StringTool;

/**
 * Learner for the parameters of conditional probability tables of fragments.
 * @author Dominik Jain
 */
public class CPTLearner extends probcog.bayesnets.learning.CPTLearner {
	
	protected HashMap<Integer, HashMap<String, Integer>> marginals;
	protected int numCounted, numNotCounted;
	protected boolean verbose;
	protected boolean debug = false;
	
	public CPTLearner(RelationalBeliefNetwork bn) throws ProbCogException {
		this(bn, false, false);
	}
	
	public CPTLearner(RelationalBeliefNetwork bn, boolean uniformDefault, boolean debug) throws ProbCogException {
		super(bn);	
		setUniformDefault(uniformDefault);
		this.debug = debug;
		//marginals = new HashMap<Integer, HashMap<String,Integer>>(); // just for debugging
	}
	
	protected void printCountStatus(boolean force) {
		int total = numCounted+numNotCounted;
		boolean doPrint = force ? true : total % 10 == 0; 
		if(verbose && doPrint) 
			System.out.printf("    %d/%d counted\r", numCounted, total);
	}
	
	/**
	 * count an example (variable setting) by incrementing the counter for the given variable
	 * @param db			the database containing propositions
	 * @param node			node of the variable for which we are counting an example
	 * @param params		the node's actual parameters
	 * @param closedWorld	whether the closed-world assumption is to be made
	 * @throws ProbCogException
	 */
	protected void processGrounding(GenericDatabase<?,?> db, RelationalNode node, String[] params, boolean closedWorld) throws ProbCogException {
		// if the node is not CPT-based, skip it
		if(!node.hasCPT())
			return;
		
		// to determine if we really have to count the example, we must
		// check if there are any decision parents and count only if all
		// decision parents are true
		Collection<DecisionNode> decisions = node.getDecisionParents();
		if(decisions.size() > 0) {
			for(DecisionNode decision : decisions) {
				if(!decision.isTrue(node.params, params, db, closedWorld)) {
					numNotCounted++;
					printCountStatus(false);
					return;
				}
			}
		}
		
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		// get the node and its associated counter
		ExampleCounter counter = this.counters[node.index];
		// get the main variable's name
		String varName = Signature.formatVarName(node.getFunctionName(), params);
		//System.out.println("counting " + varName);
		
		// obtain all groundings of the relevant variables
		ParentGrounder pg = bn.getParentGrounder(node);
		Vector<ParentGrounding> groundings = pg.getGroundings(params, db);
		if(groundings == null) {
			if(debug)
				System.err.println("Variable " + Signature.formatVarName(node.getFunctionName(), params)+ " skipped because parents could not be grounded.");
			return;
		}

		//System.out.println();
		/*HashMap<String, Integer> counts = marginals.get(node.index);
		if(counts == null) {
			counts = new HashMap<String, Integer>();
			marginals.put(node.index, counts);
		}*/
		
		double exampleWeight = 1.0;

		// do some precomputations to determine example weight
		/*
		if(false) {
			// TODO the code in this block does not yet consider the possibility of decision nodes as parents
			// - for average of conditional probabilities compute the homogeneity of the relational parents to obtain suitable example weights		
			if(node.aggregator == Aggregator.Average && node.parentMode != null && node.parentMode.equals("CP")) {
				// create a vector of counts/probabilities
				// first get the number of configurations that are possible for each parent
				int dim = 1;
				Vector<Integer> relevantParentIndices = new Vector<Integer>();
				Vector<Integer> precondParentIndices = new Vector<Integer>();
				for(RelationalNode parent : bn.getRelationalParents(node)) {
					if(parent.isPrecondition) {
						precondParentIndices.add(parent.index);
						continue;
					}
					dim *= parent.getDomain().getOrder();
					relevantParentIndices.add(parent.index);
				}
				double[] v = new double[dim];
				// gather counts
				int numExamples = 0;
				for(Map<Integer, String[]> paramSets : groundings) { // for each grounding...
					boolean skip = false;
					// check if the preconditions are met
					for(Integer nodeIdx : precondParentIndices) {
						RelationalNode ndCurrent = bn.getRelationalNode(nodeIdx);
						String value = db.getVariableValue(ndCurrent.getVariableName(paramSets.get(ndCurrent.index)), closedWorld);
						if(!value.equalsIgnoreCase("true")) {
							skip = true;
							break;
						}
					}
					if(skip) 
						continue;
					// count the example
					int factor = 1;
					int addr = 0;
					for(Integer nodeIdx : relevantParentIndices) {
						RelationalNode ndCurrent = bn.getRelationalNode(nodeIdx);
						//String value = db.getVariableValue(ndCurrent.getVariableName(paramSets.get(ndCurrent.index)), closedWorld);
						String value = ndCurrent.getValueInDB(paramSets.get(ndCurrent.index), db, closedWorld);
						Discrete dom = ndCurrent.getDomain();
						int domIdx = dom.findName(value);					
						if(domIdx < 0) {
							String[] domain = BeliefNetworkEx.getDiscreteDomainAsArray(ndCurrent.node);
							throw new ProbCogException("Could not find value '" + value + "' in domain of " + ndCurrent.toString() + " {" + StringTool.join(",", domain) + "}");
						}
						addr += factor * domIdx; 
						factor *= dom.getOrder();
					}
					v[addr] += 1;	
					numExamples++;
				}
				// obtain probabilities
				for(int i = 0; i < v.length; i++)
					v[i] = v[i] / numExamples;
				// calculate weight
				exampleWeight = 0;
				int exponent = 10;			
				for(int i = 0; i < v.length; i++) {
					exampleWeight += Math.pow(v[i], exponent);
				}
				//System.out.println("weight: " + exampleWeight);
			}
		}
		*/
		// precomputations done... now the actual counting starts
			
		// set the domain indices of all relevant nodes (node itself and parents)
		for(ParentGrounding grounding : groundings) { // for each grounding...
			Map<Integer, String[]> paramSets = grounding.nodeArgs;
			// check precondition parents			
			// TODO do we really need this? Preconditions are checked in ParentGrounder?
			boolean countExample = true;
			//System.out.println("checking preconditions of grounding of " + node.getVariableName(paramSets.get(node.index)));
			for(int i = 1; i < counter.nodeIndices.length; i++) {
				ExtendedNode extCurrent = bn.getExtendedNode(counter.nodeIndices[i]);
				if(!(extCurrent instanceof RelationalNode))
					continue;
				RelationalNode ndCurrent = (RelationalNode)extCurrent;								
				if(ndCurrent.isPrecondition) {
					String[] actualParams = paramSets.get(ndCurrent.index);
					String value = ndCurrent.getValueInDB(actualParams, db, closedWorld);					
					// preconditions are required to be "True"
					if(!value.equalsIgnoreCase("true")) {
						countExample = false;
						break;
					}					
				}				
			}
			//System.out.println("checking preconditions done");
			if(!countExample) {
				numNotCounted++;
				printCountStatus(false);
				continue;
			}

			// if preconditions were met, handle domain indices of all parents	
			// and count the example
			int domainIndices[] = new int[this.nodes.length];
			countVariableR(varName, db, closedWorld, bn, paramSets, counter, domainIndices, exampleWeight, 0);
			numCounted++;
			
			if(debug && verbose) { // just debug output
				StringBuffer condition = new StringBuffer();
				for(Entry<Integer, String[]> e : paramSets.entrySet()) {
					if(e.getKey() == node.index)
						continue;
					RelationalNode rn = bn.getRelationalNode(e.getKey());
					condition.append(' ');
					condition.append(rn.getVariableName(e.getValue()));
					condition.append('=');
					condition.append(rn.getDomain().getName(domainIndices[rn.index]));
				}
				System.out.println("    " + node.getVariableName(params) + "=" + node.getDomain().getName(domainIndices[node.index]) + " |" + condition);
			}
			
			// keep track of counts (just debugging)
			/*String v = node.node.getDomain().getName(domainIndices[counter.nodeIndices[0]]);
			Integer i = counts.get(v);
			if(i == null)
				i = 0;
			counts.put(v, i+1);*/
		}
	}
	
	/**
	 * helper function that recursively sets the domain indices of parents to learn an entry 
	 */
	protected void countVariableR(String varName, GenericDatabase<?,?> db, boolean closedWorld, RelationalBeliefNetwork bn, Map<Integer, String[]> paramSets, ExampleCounter counter, int[] domainIndices, double exampleWeight, int i) throws ProbCogException {
		// count the example
		if(i == counter.nodeIndices.length) {
			counter.count(domainIndices, exampleWeight);
			printCountStatus(false);
			return;
		}
		
		int domain_idx = -1;
		ExtendedNode extCurrent = bn.getExtendedNode(counter.nodeIndices[i]);
		// decision node parents are always true, because we use them to define hard constraints on the use of the CPT we are learning;
		// whether the constraint that they represent is actually satisfied was checked beforehand
		if(extCurrent instanceof DecisionNode) {
			domainIndices[extCurrent.index] = 0; // 0 is true
			countVariableR(varName, db, closedWorld, bn, paramSets, counter, domainIndices, exampleWeight, i+1);
		}
		// it's a regular parent
		else {
			// get the corresponding RelationalNode object
			RelationalNode ndCurrent = (RelationalNode)extCurrent;
			// side affair: learn the CPT of constant nodes here by incrementing the counter
			if(ndCurrent.isConstant) {
				String[] actualParams = paramSets.get(ndCurrent.index);
				domainIndices[ndCurrent.index] = ndCurrent.getDomain().findName(actualParams[0]);
				this.counters[ndCurrent.index].count(domainIndices);
				countVariableR(varName, db, closedWorld, bn, paramSets, counter, domainIndices, exampleWeight, i+1);
			}
			// preconditions were handled above/in ParentGrounder
			else if(ndCurrent.isPrecondition) {
				domainIndices[extCurrent.index] = 0; // 0 is true
				countVariableR(varName, db, closedWorld, bn, paramSets, counter, domainIndices, exampleWeight, i+1);
			}
			else {
				// determine the value of the node given the parameter settings implied by the main node
				String[] actualParams = paramSets.get(ndCurrent.index);
				if(actualParams == null) {
					Vector<String> availableNodes = new Vector<String>();
					for(Integer idx : paramSets.keySet())
						availableNodes.add(idx.toString() + "/" + ndCurrent.getNetwork().getRelationalNode(idx).toString());
					throw new ProbCogException("Relevant node " + ndCurrent.index + "/" + ndCurrent + " has no grounding for main node instantiation " + varName + "; have only " + availableNodes.toString());
				}
				Object value = db.getVariableValue(ndCurrent.getVariableName(actualParams), closedWorld); //ndCurrent.getValueInDB(actualParams, db, closedWorld);
				if(value == null)
					throw new ProbCogException(String.format("Could not find setting for node named '%s' while processing '%s'", ndCurrent.getName(), varName));
				// get the current node's domain and the index of its setting
				Discrete dom = (Discrete)(ndCurrent.node.getDomain());
				if(value instanceof String) {					
					domain_idx = dom.findName((String)value);
					if(domain_idx == -1) {	
						String[] domElems = new String[dom.getOrder()];
						for(int j = 0; j < domElems.length; j++)
							domElems[j] = dom.getName(j);
						throw new ProbCogException(String.format("'%s' not found in domain of %s {%s} while processing %s", value, ndCurrent.getVariableName(actualParams), StringTool.join(",", domElems), varName));
					}
					domainIndices[extCurrent.index] = domain_idx;
					countVariableR(varName, db, closedWorld, bn, paramSets, counter, domainIndices, exampleWeight, i+1);
				}
				else if(value instanceof ValueDistribution) {
					ValueDistribution vd = (ValueDistribution)value;
					for(Entry<String,Double> e : vd.entrySet()) {
						domain_idx = dom.findName((String)e.getKey());
						if(domain_idx == -1) {
							String[] domElems = new String[dom.getOrder()];
							for(int j = 0; j < domElems.length; j++)
								domElems[j] = dom.getName(j);
							throw new ProbCogException(String.format("'%s' not found in domain of %s {%s} while processing %s", e.getKey(), ndCurrent.getFunctionName(), StringTool.join(",", domElems), varName));
						}					
						domainIndices[extCurrent.index] = domain_idx;
						double p = e.getValue();
						if(p > 0)
							countVariableR(varName, db, closedWorld, bn, paramSets, counter, domainIndices, exampleWeight * p, i+1);
					}
				}				
			}
		}	
		
	}
	
	/**
	 * learn the CPTs from only the data that is given in the database (relations not in the database are not considered because the closed-world assumption is not being made)
	 * @param db
	 * @throws ProbCogException
	 */
	@Deprecated
	public void learn(Database db) throws ProbCogException {
		throw new ProbCogException("No longer supported");
		/*for(Variable var : db.getEntries()) {
			countVariable(db, var.nodeName, var.params, false); // TODO: the node used is the one with the most parents that fits			
		}*/
	}

	/**
	 * generates for all nodes all the possible parameters (using the node signatures and domain elements from the database) and counts the corresponding examples 
	 * @param db
	 * @param closedWorld
	 * @param verbose
	 * @throws ProbCogException
	 */
	public void learnTyped(GenericDatabase<?,?> db, boolean closedWorld, boolean verbose) throws ProbCogException {
		if(!initialized) init();
		
		this.verbose = verbose;
		RelationalBeliefNetwork bn = (RelationalBeliefNetwork)this.bn;
		
		// construct parent grounders for relevant nodes
		// (to check early on whether the structure is OK)
		for(RelationalNode node : bn.getRelationalNodes()) {
			if(node.isConstant || node.isBuiltInPred() || !node.hasCPT())
				continue;
			node.getParentGrounder();
		}
 
		// learn CPTs
		for(RelationalNode node : bn.getRelationalNodes()) { // for each node...
			
			// ignore constant nodes as they do not correspond to logical atoms
			if(node.isConstant || node.isBuiltInPred())  
				continue;

			if(verbose)
				System.out.println("  " + node.getName());				
			
			// for precondition nodes, simply set CPT to 100% true
			if(node.isPrecondition) {
				CPF cpf = node.node.getCPF();
				int numColumns = cpf.getRowLength(); // should be 1 (just in case)
				ValueDouble v1 = new ValueDouble(1.0);
				ValueZero zero = new ValueZero();
				for(int i = 0; i < numColumns; i++) { 
					cpf.put(i, v1);				
					cpf.put(i+cpf.getColumnValueAddressOffset(), zero);
				}
				continue;
			}
			
			// for auxiliary nodes, init to uniform distribution
			if(node.isAuxiliary) {
				CPF cpf = node.node.getCPF();
				int numRows = cpf.getDomainProduct()[0].getDomain().getOrder();
				ValueDouble v = new ValueDouble(1.0 / numRows);
				for(int i = 0; i < cpf.size(); i++) {
					cpf.put(i, v);				
				}
				continue;
			}
			
			numCounted = 0;
			numNotCounted = 0;
			// consider all possible bindings for the node's parameters and count
			String[] params = new String[node.params.length];			
			processAllGroundings(db, node, params, bn.getSignature(node.getFunctionName()).argTypes, 0, closedWorld);
			if(verbose) {
				printCountStatus(true);
				System.out.println();
			}
			//System.out.println("    counts: " + marginals.get(node.index));
		}
	}
	
	/**
	 * generates all groundings (possible lists of parameters) of the node with the given name and counts the corresponding example
	 * @param db			the database (containing domains and propositions) to use
	 * @param nodeName		name of the node for which to count examples
	 * @param params	 	current list of parameters
	 * @param domainNames	list of domain names, with one entry for each parameter
	 * @param i				index into params at which to insert the next parameter 
	 * @param closedWorld	whether to make the closed-world assumption
	 * @throws ProbCogException
	 */
	protected void processAllGroundings(GenericDatabase<?,?> db, RelationalNode node, String[] params, String[] domainNames, int i, boolean closedWorld) throws ProbCogException {
		// if we have the full set of parameters, count the example
		if(i == params.length) {
			
			if(!closedWorld) {
				String varName = Signature.formatVarName(node.getFunctionName(), params);
				if(!db.contains(varName))
					throw new ProbCogException("Incomplete data: No value for " + varName);
			}
			
			processGrounding(db, node, params, closedWorld);
			return;
		}
		
		// otherwise consider all ways of extending the current list of parameters 
		// using the domain elements that are applicable		
		if(RelationalNode.isConstant(node.params[i])) {
			params[i] = node.params[i];
			processAllGroundings(db, node, params, domainNames, i+1, closedWorld);
		}
		else {
			Iterable<String> domain = db.getDomain(domainNames[i]);
			if(domain == null)
				throw new ProbCogException("Error while grounding " + node + ": Domain " + domainNames[i] + " not found or is empty.");
			for(String element : domain) {
				params[i] = element;
				processAllGroundings(db, node, params, domainNames, i+1, closedWorld);	
			}
		}
	}
}
