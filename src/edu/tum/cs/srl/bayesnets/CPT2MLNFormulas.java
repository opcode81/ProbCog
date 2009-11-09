package edu.tum.cs.srl.bayesnets;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import weka.classifiers.trees.J48;
import weka.classifiers.trees.j48.Rule;
import weka.classifiers.trees.j48.Rule.Condition;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.logic.Conjunction;
import edu.tum.cs.logic.Formula;

public class CPT2MLNFormulas {
	protected RelationalBeliefNetwork rbn;
	protected RelationalNode mainNode;
	protected String additionalPrecondition;
	
	public CPT2MLNFormulas(RelationalNode node) {
		this.mainNode = node;
		this.rbn = node.getNetwork();
		additionalPrecondition = null;
	}
	
	/**
	 * adds a precondition that must be added to each conjunction that is generated 
	 * @param cond
	 */
	public void addPrecondition(String cond) {
		if(additionalPrecondition == null)
			additionalPrecondition = cond;
		else
			additionalPrecondition += " ^ " + cond;
	}
	
	public String getPrecondition() {
		return additionalPrecondition;
	}
	
	/**
	 * executes the conversion of the CPT to MLN formulas and prints the formulas to the given stream
	 * @param out
	 */
	public void convert(PrintStream out) {
		try {
			CPT2Rules cpt2rules = new CPT2Rules(mainNode);
			
			// if the main node has constant parents, then we must learn a separate decision tree for each 
			// configuration of the constants
			for(HashMap<String, String> constantAssignment : mainNode.getConstantAssignments()) {		
				Rule[] rules = cpt2rules.learnRules(constantAssignment);
				
				// write weighted formulas for each of the decision tree's leaf nodes/rules
				for(Rule rule : rules) {
					if(!rule.hasAntds()) // if the rule has no antecedent (it must be the only rule) and it means that the distribution must be a uniform distribution, so we do not need any formulas
						continue;
					StringBuffer conjunction = new StringBuffer();
					int lits = 0;
					// generate literals for each of the nodes along the path from the root to a leaf
					// Note: it is not guaranteed that each rule contains a check against the leaf node, so keep track if we ran across it
					boolean haveMainNode = false;
					for(Condition c : rule.getAntecedent()) {
						RelationalNode node = cpt2rules.getRelationalNode(c);
						if(node == mainNode)
							haveMainNode = true;
						String literal = node.toLiteralString(rbn.getDomainIndex(node.node, c.getValue()), constantAssignment);
						if(lits++ > 0)
							conjunction.append(" ^ ");
						conjunction.append(literal);
					}
					// add preconditions 
					// TODO handle decision parents
					for(RelationalNode parent : rbn.getRelationalParents(mainNode)) {
						if(parent.isPrecondition) {
							if(lits++ > 0)
								conjunction.append(" ^ ");
							conjunction.append(parent.toLiteralString(rbn.getDomainIndex(parent.node, "True"), constantAssignment));
						}
					}
					// if we did not come across the main node above, create one variant of the conjunction for each possible setting
					Vector<String> conjunctions = new Vector<String>();
					if(!haveMainNode) {
						for(int i = 0; i < mainNode.node.getDomain().getOrder(); i++) {
							conjunctions.add(conjunction.toString() + " ^ " + mainNode.toLiteralString(i, null));
						}
					}
					else
						conjunctions.add(conjunction.toString());
					// write final formulas with weights			
					double prob = Double.parseDouble(rule.getConsequent().getValue());
					double weight = prob == 0.0 ? -100 : Math.log(prob);
					for(String conj : conjunctions) {
						out.print(weight + " ");
						out.print(conj);
						if(additionalPrecondition != null)
							out.print(" ^ " + additionalPrecondition);
						out.println();
					}
				}		
			}
		} 
		catch (Exception e) {			
			e.printStackTrace();
		}		
	}
	
	/**
	 *  the task here is to learn a decision tree as a compact representation of a 
	 *  CPT, the predictors being the values of all nodes relevant to the CPT (parents and child)
	 *  and the predicted class attribute being the probability value
	 * @author jain
	 */
	public static class CPT2Rules {
		/**
		 * maps attribute names to actual attributes. Note: except for the probability value attribute "prob", the names of attributes correspond to names of relational node
		 */
		protected HashMap<String, Attribute> attrs;
		protected RelationalBeliefNetwork rbn;
		protected CPF cpf;
		BeliefNode[] nodes;
		FastVector fvAttribs;
		HashMap<Attribute, RelationalNode> relNodes;
		RelationalNode mainNode;
		int zerosInCPT;
		
		public CPT2Rules(RelationalNode relNode) {
			mainNode = relNode;
			rbn = relNode.getNetwork();
			cpf = relNode.node.getCPF();
			nodes = cpf.getDomainProduct();			
			
			// the vector of attributes			
			fvAttribs = new FastVector(nodes.length+1);
			attrs = new HashMap<String,Attribute>();
			
			// generate the predictor attributes (one attribute for each of the parents and the node itself)
			relNodes = new HashMap<Attribute, RelationalNode>();
			for(BeliefNode node : nodes) {		
				ExtendedNode extNode = rbn.getExtendedNode(node);
				if(extNode instanceof DecisionNode)
					continue;
				Discrete dom = (Discrete)node.getDomain();
				FastVector attValues = new FastVector(dom.getOrder());
				for(int i = 0; i < dom.getOrder(); i++)
					attValues.addElement(dom.getName(i));
				Attribute attr = new Attribute(node.getName(), attValues);				
				attrs.put(node.getName(), attr);
				relNodes.put(attr, rbn.getRelationalNode(node));
				fvAttribs.addElement(attr);
			}
			
			// add class (predicted) attribute, which here is the probability value
			// - collect set of values
			TreeSet<Double> probs = new TreeSet<Double>();
			zerosInCPT = 0;
			walkCPT4ValueSet(new int[nodes.length], 0, probs);
			FastVector attrValues = new FastVector(probs.size());
			for(Double d : probs) 
				attrValues.addElement(Double.toString(d));
			// - add attribute
			Attribute probAttr = new Attribute("prob", attrValues);
			attrs.put("prob", probAttr);
			fvAttribs.addElement(probAttr);		
		}

		protected void walkCPT4ValueSet(int[] addr, int i, Set<Double> values) {
			BeliefNode[] nodes = cpf.getDomainProduct();
			if(i == addr.length) { // we have a complete address
				// get the probability value
				int realAddr = cpf.addr2realaddr(addr);
				double value = ((ValueDouble)cpf.get(realAddr)).getValue();
				if(value == 0.0)
					zerosInCPT++;
				values.add(value);
			}
			else { // the address is yet incomplete -> consider all ways of setting the next e
				Discrete dom = (Discrete)nodes[i].getDomain();
				ExtendedNode extNode = rbn.getExtendedNode(nodes[i]);
				if(extNode instanceof DecisionNode) {
					addr[i] = 0; // True
					walkCPT4ValueSet(addr, i+1, values);
				}
				else {
					RelationalNode n = (RelationalNode)extNode;
					if(n.isPrecondition) {
						addr[i] = dom.findName("True");
						walkCPT4ValueSet(addr, i+1, values);
					}
					else {
						for(int j = 0; j < dom.getOrder(); j++) {
							addr[i] = j;
							walkCPT4ValueSet(addr, i+1, values);
						}
					}
				}
			}	
		}
		
		public int getZerosInCPT() {
			return zerosInCPT;
		}
		
		/**
		 * collects instances for the given constant assignment and learns a decision tree for which it returns the set of rules
		 * @param constantAssignment
		 * @return
		 * @throws Exception
		 */
		public Rule[] learnRules(Map<String, String> constantAssignment) throws Exception {
			// collect instances
			Instances instances = new Instances("foo", fvAttribs, 60000);
			walkCPT4InstanceCollection(new int[nodes.length], 0, constantAssignment, instances);
			
			// learn a J48 decision tree from the instances
			instances.setClass(attrs.get("prob"));
			J48 j48 = new J48();
			j48.setUnpruned(true);
			j48.setMinNumObj(0); // there is no minimum number of objects that has to end up at each of the tree's leaf nodes 
			j48.buildClassifier(instances);
			
			// output the decision tree
			//System.out.println(j48);
			
			return j48.getRules();
		}
		
		protected void walkCPT4InstanceCollection(int[] addr, int i, Map<String,String> constantSettings, Instances instances) throws Exception {
			BeliefNode[] nodes = cpf.getDomainProduct();
			if(i == addr.length) { // we have a complete address
				// get the probability value
				int realAddr = cpf.addr2realaddr(addr);
				double value = ((ValueDouble)cpf.get(realAddr)).getValue();
				
				// create a new instance
				Instance inst = new Instance(nodes.length+1);
				
				// translate the address to attribute settings
				for(int j = 0; j < addr.length; j++) {					
					Attribute attr = attrs.get(nodes[j].getName());
					if(attr != null) {
						Discrete dom = (Discrete)nodes[j].getDomain();
						inst.setValue(attr, dom.getName(addr[j]));
					}
				}
				// add value of class (predicted) attribute - i.e. the probability value 
				inst.setValue(attrs.get("prob"), Double.toString(value));
				
				// add the instance to our collection
				instances.add(inst);
			}
			else { // the address is yet incomplete -> consider all ways of setting the next e
				Discrete dom = (Discrete)nodes[i].getDomain();
				ExtendedNode extNode = rbn.getExtendedNode(nodes[i]);
				if(extNode instanceof DecisionNode) {
					addr[i] = 0; // True
					walkCPT4InstanceCollection(addr, i+1, constantSettings, instances);
				}
				else {
					RelationalNode n = (RelationalNode)extNode;
					if(n.isPrecondition) {
						addr[i] = dom.findName("True");
						if(addr[i] == -1)
							throw new Exception("The node " + nodes[i] + " is set as a precondition, but its domain does not contain the value 'True'.");
						walkCPT4InstanceCollection(addr, i+1, constantSettings, instances);
					}
					else if(n.isConstant) { 
						addr[i] = dom.findName(constantSettings.get(n.getName()));
						walkCPT4InstanceCollection(addr, i+1, constantSettings, instances);
					}
					else {
						for(int j = 0; j < dom.getOrder(); j++) {
							addr[i] = j;
							walkCPT4InstanceCollection(addr, i+1, constantSettings, instances);
						}
					}
				}
			}	
		}
		
		/**
		 * gets the relational node that corresponds to the attribute that is being checked against in the given condition
		 * @param c
		 * @return
		 */
		public RelationalNode getRelationalNode(Condition c) {
			return relNodes.get(c.getAttribute());
		}
		
		public Formula getConjunction(Rule rule, Map<String,String> constantAssignment) throws Exception {
			boolean haveMainNode = false;
			Vector<Formula> conjuncts = new Vector<Formula>();
			for(Condition c : rule.getAntecedent()) {
				RelationalNode node = this.getRelationalNode(c);
				if(node == mainNode)
					haveMainNode = true;
				int value = rbn.getDomainIndex(node.node, c.getValue());
				Formula literal = node.toFormula(value, constantAssignment);
				conjuncts.add(literal);
			}
			return new Conjunction(conjuncts);
		}
		
		public double getProbability(Rule r) {
			return Double.parseDouble(r.getConsequent().getValue());
		}
	}	
}
