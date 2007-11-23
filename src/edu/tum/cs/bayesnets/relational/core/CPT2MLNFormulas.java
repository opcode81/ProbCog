package edu.tum.cs.bayesnets.relational.core;

import java.io.PrintStream;
import java.util.HashMap;
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

public class CPT2MLNFormulas {
	protected HashMap<String, Attribute> attrs;
	protected CPF cpf;
	protected RelationalBeliefNetwork bn;
	protected RelationalNode mainNode;
	
	public CPT2MLNFormulas(RelationalNode node) {
		this.mainNode = node;
		this.bn = node.getNetwork();
		this.cpf = node.node.getCPF();
		attrs = new HashMap<String, Attribute>();
	}
	
	/**
	 * executes the conversion of the CPT to MLN formulas and prints the formulas to the given stream
	 * @param out
	 */
	public void convert(PrintStream out) {
		try {
			// the task here is to learn a decision tree as a compact representation of a 
			// CPT, the predictors being the values of all nodes relevant to the CPT (parents and child)
			// and the predicted class attribute being the probability value
			
			BeliefNode[] nodes = cpf.getDomainProduct();
			
			// the vector of attributes
			FastVector fvAttribs = new FastVector(nodes.length+1);
			
			// generate the predictor attributes (one attribute for each of the parents and the node itself)
			HashMap<Attribute, RelationalNode> relNodes = new HashMap<Attribute, RelationalNode>();
			for(BeliefNode node : nodes) {				
				Discrete dom = (Discrete)node.getDomain();
				FastVector attValues = new FastVector(dom.getOrder());
				for(int i = 0; i < dom.getOrder(); i++)
					attValues.addElement(dom.getName(i));
				Attribute attr = new Attribute(node.getName(), attValues);				
				attrs.put(node.getName(), attr);
				relNodes.put(attr, bn.getRelationalNode(node));
				fvAttribs.addElement(attr);
			}
		
			// add class (predicted) attribute
			// - collect set of values
			TreeSet<Double> probs = new TreeSet<Double>();
			walkCPT4ValueSet(new int[nodes.length], 0, probs);
			FastVector attrValues = new FastVector(probs.size());
			for(Double d : probs) 
				attrValues.addElement(Double.toString(d));
			// - add attribute
			Attribute probAttr = new Attribute("prob", attrValues);
			attrs.put("prob", probAttr);
			fvAttribs.addElement(probAttr);			

			// collect instances
			Instances instances = new Instances("foo", fvAttribs, 60000);
			walkCPT4InstanceCollection(new int[nodes.length], 0, instances);
			
			// learn a J48 decision tree from the instances
			instances.setClass(attrs.get("prob"));
			J48 j48 = new J48();
			j48.setUnpruned(true);
			j48.setMinNumObj(0); // there is no minimum number of objects that has to end up at each of the tree's leaf nodes 
			j48.buildClassifier(instances);		
			
			// write weighted formulas for each of the decision tree's leaf nodes/rules
			for(Rule rule : j48.getRules()) {
				if(!rule.hasAntds()) // if the rule has no antecedent (it must be the only rule) and it means that the distribution must be a uniform distribution, so we do not need any formulas
					continue;
				StringBuffer conjunction = new StringBuffer();
				int lits = 0;
				// generate literals for each of the nodes along the path from the root to a leaf
				// Note: it is not guaranteed that each rule contains a check against the leaf node, so keep track if we ran across it
				boolean haveMainNode = false;
				for(Condition c : rule.getAntecedent()) {
					RelationalNode node = relNodes.get(c.getAttribute());
					if(node == mainNode)
						haveMainNode = true;
					String literal = node.toLiteral(bn.getDomainIndex(node.node, c.getValue()), null);
					if(lits++ > 0)
						conjunction.append(" ^ ");
					conjunction.append(literal);
				}
				// add preconditions to all conjunctions
				RelationalNode[] parents = bn.getRelationalParents(mainNode);
				for(RelationalNode parent : parents) {
					if(parent.isPrecondition) {
						if(lits++ > 0)
							conjunction.append(" ^ ");
						conjunction.append(parent.toLiteral(bn.getDomainIndex(parent.node, "True"), null));
					}
				}
				// if we did not come across the main node above, create one variant of the conjunction for each possible setting
				Vector<String> conjunctions = new Vector<String>();
				if(!haveMainNode) {
					for(int i = 0; i < mainNode.node.getDomain().getOrder(); i++) {
						conjunctions.add(conjunction.toString() + " ^ " + mainNode.toLiteral(i, null));
					}
				}
				else
					conjunctions.add(conjunction.toString());
				// write final formulas with weights			
				double prob = Double.parseDouble(rule.getConsequent().getValue());
				double weight = prob == 0.0 ? -100 : Math.log(prob);
				for(String conj : conjunctions) {
					out.print(weight + " ");
					out.println(conj);
				}
			}		
			
			// output the decision tree
			//System.out.println(j48);			
		} 
		catch (Exception e) {			
			e.printStackTrace();
		}		
	}
	
	protected void walkCPT4InstanceCollection(int[] addr, int i, Instances instances) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		if(i == addr.length) { // we have a complete address
			// get the probability value
			int realAddr = cpf.addr2realaddr(addr);
			double value = ((ValueDouble)cpf.get(realAddr)).getValue();
			
			// create a new instance
			Instance inst = new Instance(nodes.length+1);
			
			// translate the address to attribute settings
			for(int j = 0; j < addr.length; j++) {
				Discrete dom = (Discrete)nodes[j].getDomain();
				inst.setValue(attrs.get(nodes[j].getName()), dom.getName(addr[j]));
			}
			// add value of class (predicted) attribute - i.e. the probability value 
			inst.setValue(attrs.get("prob"), Double.toString(value));
			
			// add the instance to our collection
			instances.add(inst);
		}
		else { // the address is yet incomplete -> consider all ways of setting the next e
			Discrete dom = (Discrete)nodes[i].getDomain();
			RelationalNode n = bn.getRelationalNode(nodes[i]);
			if(n.isPrecondition) {
				addr[i] = dom.findName("True");
				if(addr[i] == -1)
					throw new Exception("The node " + nodes[i] + " is set as a precondition, but its domain does not contain the value 'True'.");
				walkCPT4InstanceCollection(addr, i+1, instances);
			}
			else {
				for(int j = 0; j < dom.getOrder(); j++) {
					addr[i] = j;
					walkCPT4InstanceCollection(addr, i+1, instances);
				}
			}
		}	
	}

	protected void walkCPT4ValueSet(int[] addr, int i, Set<Double> values) {
		BeliefNode[] nodes = cpf.getDomainProduct();
		if(i == addr.length) { // we have a complete address
			// get the probability value
			int realAddr = cpf.addr2realaddr(addr);
			double value = ((ValueDouble)cpf.get(realAddr)).getValue();
			values.add(value);
		}
		else { // the address is yet incomplete -> consider all ways of setting the next e
			Discrete dom = (Discrete)nodes[i].getDomain();
			RelationalNode n = bn.getRelationalNode(nodes[i]);
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
