/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
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
package probcog.rpt;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import kdl.prox3.model.classifiers.RPNode;
import kdl.prox3.model.estimators.DiscreteEstimator;
import kdl.prox3.model.estimators.ProbDistribution;
import kdl.prox3.model.features.Feature;
import kdl.prox3.model.features.FeatureSetting;
import kdl.prox3.script.ItemModelAttr;

/**
 * Converts relational probability trees to sets of logical implications.
 * @author Dominik Jain
 */
public class RPT2Logic {
	
	protected String coreItemName, coreItemVar, attribute;
	protected Vector<Path> paths;
	protected HashMap<String, ObjectType> objects;
	protected HashMap<String, RelationType> relations;
	protected ObjectType coreObject;
	protected boolean walkRecursive;
	protected String precondition = "";
	/**
	 * the total number of samples that was used to learn the RPT that is currently being processed
	 */
	protected double totalSamplesInTree;
	/**
	 * used during walkTree to store the name of the variable that the current node deals with 
	 */
	protected String currentNodeObjectVar;
	
	/**
	 * constructor to use if the core item is an object
	 * @param attribute
	 * @param coreObjectName
	 * @param coreObjectVar
	 */
	public RPT2Logic(String attribute, String coreObjectName, String coreObjectVar) {
		objects = new HashMap<String, ObjectType>();
		paths = new Vector<Path>();
		relations = new HashMap<String, RelationType>();
		this.coreItemName = coreObjectName;
		this.coreItemVar = coreObjectVar;
		this.attribute = attribute;
		this.coreObject = new ObjectType(coreObjectName, coreObjectVar, null, null);
	}
	
	/**
	 * constructor to use if the core item is a relation 
	 * @param attribute
	 * @param coreRelationName
	 */
	public RPT2Logic(String attribute, String coreRelationName) {
		objects = new HashMap<String, ObjectType>();
		paths = new Vector<Path>();
		relations = new HashMap<String, RelationType>();
		this.coreItemName = coreRelationName;
		this.attribute = attribute;	
		this.coreObject = null;
	}	
	
	public ObjectType getCoreObject() {
		return coreObject;
	}
	
	public void setCoreItemVar(String var) {
		this.coreItemVar = var;
		if(coreObject != null)
			this.coreObject.setVarName(var);
	}
	
	/**
	 * traverses the relational probability tree rooted at root and collects all the paths to leaf nodes
	 * (a path thus contains a number of atomic formulas which correspond to the tests in the various nodes)
	 * @param root root of the relational probability tree
	 */
	public void walkTree(RPNode root) {
		// get the number of samples used to induce the tree
		DiscreteEstimator de = root.getClassLabelDistribution();
		ProbDistribution dist = de.getProbDistribution();
		this.totalSamplesInTree = dist.getTotalNumValues();

		walkTree(root, new Path());
	}
	
	protected class DistributionValue implements Comparable<DistributionValue> {
		public String value;
		public double probability;
		
		public DistributionValue(String value, Double count, double total) {
			this.value = value;
			this.probability = count/total;
		}

		public int compareTo(DistributionValue o) { // sort in reverse order of probability
			return (int)-Math.signum(probability - o.probability);
		}		
	}
	
	protected boolean isBooleanValue(String value) {
		return value.equals("True") || value.equals("False");
	}
	
	protected class Path implements Cloneable {
		/**
		 * a vector of strings, where each string is a formula (usually an atomic formula) that
	     * corresponds to a test at a node along the path.
		 */
		public Vector<String> items;
		public HashSet<ObjectType> precondition;
		/**
		 * the percentage of samples used to build up the formula that is represented by the path
		 */
		protected double support;
		/**
		 * the accuracy estimating the ratio of successfully classified instances
		 */
		protected double accuracy;
		
		public Path() {
			precondition = new HashSet<ObjectType>();
			items = new Vector<String>();
		}
		
		@SuppressWarnings("unchecked")
		public Path clone() {
			Path p = new Path();
			p.items = (Vector<String>)items.clone();
			p.precondition = (HashSet<ObjectType>)precondition.clone();
			return p;
		}
		
		public String toString() {
			return items.toString();
		}
	}
	
	protected String getAtom(Path path, String predicate, String item, String value) {
		// the attribute name is always the name of the predicate, the parameters follow in parentheses
		String atom = predicate + "(";
		// is the item the attribute (predicate name) belongs to a relation/link?
		RelationType li = this.relations.get(item);
		if(li != null) { // yes -> add the two connected object variables as parameters  
			atom += li.from.varName + ", " + li.to.varName;
			path.precondition.add(li.from);
			path.precondition.add(li.to);
			// only boolean values are allowed as attribute values of links -> check!
			if(!this.isBooleanValue(value)) {
				System.err.println("  Warning: only boolean value allowed as link attribute value - got " + value);
				walkRecursive = false;
			}
		}
		else { // the item is not a relation but an object
			// if the item is the core item, add the variable name of the core object as the first parameter
			if(item.equals(coreItemName))
				atom += (this.currentNodeObjectVar = coreItemVar);
			// otherwise, the item is a related object
			else {
				ObjectType rel = this.objects.get(item);
				if(rel != null) { // yes -> the first argument is the variable name of the related object
					atom += (this.currentNodeObjectVar = rel.varName);
					path.precondition.add(rel); // add the precondition
				}
				else {
					// neither related object nor link -> don't know what to do! 
					System.err.println("  Warning: unhandled link/relation to " + item);
					walkRecursive = false;
				}
			}
		}
		if(isBooleanValue(value)) 
			atom = (value.equals("False") ? "!" : "") + atom + ")";
		else
			atom += ", " + value + ")";
		return atom;
	}
	
	protected void walkTree(RPNode node, Path path) {
		walkRecursive = true;
	
		// if the node is a leaf, we have a probability distribution
		if(node.isLeaf()) {
			// get the values sorted in descending order of probability 
			DiscreteEstimator de = node.getClassLabelDistribution();
			ProbDistribution dist = de.getProbDistribution();
			Map<?,?> distMap = dist.getDistributionMap();
			Iterator<?> iEntry = distMap.entrySet().iterator();
			TreeSet<DistributionValue> sortedVals = new TreeSet<DistributionValue>();
			while(iEntry.hasNext()) {
				Map.Entry<?,?> entry = (Map.Entry<?,?>) iEntry.next();
				sortedVals.add(new DistributionValue((String)entry.getKey(), (Double)entry.getValue(), dist.getTotalNumValues()));
			}
			// set support for this path
			path.support = dist.getTotalNumValues() / this.totalSamplesInTree;
			// add the most probable outcome as the first disjunct
			int numDisjuncts = 1;
			Iterator<DistributionValue> iter = sortedVals.iterator();
			DistributionValue mode = iter.next();			
			double prob = mode.probability;
			path.accuracy = prob;
			String formula = getAtom(path, this.attribute, this.coreItemName, mode.value);
			// add further conjuncts if the values have high probabilities (compared to the last added item)
			while(iter.hasNext()) {
				DistributionValue v = iter.next();
				if(v.probability > prob*0.6666) {
					formula += " v " + getAtom(path, this.attribute, this.coreItemName, v.value);
					numDisjuncts++;
					path.accuracy += v.probability;
				}
				else
					break;
				prob = v.probability;
			}
			// if all values were added as conjuncts, this formula is useless, so don't add it
			if(numDisjuncts == sortedVals.size()) {
				System.err.println("  Note: trivial clause for path " + path);
				return;
			}
			// add the formula to the path
			path.items.add(formula);
			// add this path
			paths.add(path);
			//System.out.println(path);
			return;
		}		
		
		// determine the atomic formula represented by this node
		ItemModelAttr attr = node.getNodeAttr();
		FeatureSetting featureSetting = node.getBestFeature();
		Feature feature = featureSetting.getFeature();
		String featureSig = feature.getShortSignature();
		String attrName = attr.getAttrName();
		String operator = feature.getOperator();
		String item = featureSetting.getItem(); // the item whose attribute is being tested at the current node
		Double threshold = Double.parseDouble(featureSetting.getThreshold());
		String value = featureSetting.getValue();
		String atom = getAtom(path, attrName, item, value);
		
		// determine the formula according to the feature type
		String formula = new String();
		if(featureSig.equals("Count")) {		
			if(operator.equals(">=")) {
				if(threshold == 1.0) {
					formula = atom;
				}
				else {
					System.err.println("  Warning: unsupported threshold " + threshold + " for feature type " + featureSig);
					return;
					/*if(this.currentNodeObjectVar == null) {
						System.err.println("  Warning: cannot handle threshold " + threshold + " for feature type " + featureSig + " - no object variable!");
						return;
					}
					int count = threshold.intValue();
					formula = "(EXIST ";
					for(int i = 1; i <= count; i++) {
						if(i > 1)
							formula += ",";						
						formula += this.currentNodeObjectVar + i;						
					}
					// !!! add preconditions!!!
					formula += " ";
					for(int i = 1; i <= count; i++) {
						if(i > 1)
							formula += " ^ ";
						formula += atom.replace("(" + this.currentNodeObjectVar, "(" + this.currentNodeObjectVar + i);
					}
					formula += ")";*/
				}
			}
			else {
				System.err.println("  Warning: unsupported operator " + operator + " for feature type " + featureSig);
				return;
			}
		}
		else {
			System.err.println("  Warning: unsupported feature type " + featureSig + " (" + path + ")");
			return;
		}
		
		// recurse 
		if(walkRecursive) {
			Path path2 = (Path) path.clone();
			path.items.add(formula);
			walkTree(node.getYesSubtree(), path);
			String negatedFormula = formula.charAt(0) == '!' ? formula.substring(1) : "!" + formula; // avoid double negation 			
			path2.items.add(negatedFormula);
			walkTree(node.getNoSubtree(), path2);
		}
	}
	
	public String createImplication(Path path, int idx, boolean bConsequentIdx) {
		String formula = new String();
		
		// if there is a precondition, add it
		String precondition = this.precondition;
		// - precondition from related objects
		if(!path.precondition.isEmpty()) {
			Iterator<ObjectType> iRelation = path.precondition.iterator();
			while(iRelation.hasNext()) {
				ObjectType rel = iRelation.next();
				String precondition2 = rel.getPrecondition(path.precondition);
				if(!precondition2.equals("")) {
					if(!precondition.equals(""))
						precondition += " ^ ";
					precondition += precondition2;
				}
			}
		}
		// - basic precondition when the core item is a relation: the relation must hold 
		if(relations.containsKey(coreItemName)) {
			RelationType rel = relations.get(coreItemName);
			String precondition2 = this.coreItemName + "(" + rel.from.varName + ", " + rel.to.varName + ")";
			// add this basic precondition only if it isn't already contained in the precondition we obtained up to here  
			if(!precondition.contains(precondition2)) {
				if(!precondition.equals(""))
					precondition += " ^ ";
				precondition += precondition2;
			}					
		}
		// - add the precondition to the beginning of the formula; the remainder of the formula is the consequent of an implication
		if(!precondition.equals(""))
			formula += precondition + " => (";
		
		if(!bConsequentIdx)
			formula += path.items.get(idx) + " => ";
		boolean empty = true;
		for(int i = 0; i < path.items.size(); i++) {
			if(i != idx) {
				if(!empty)
					formula += " ^ ";
				formula += path.items.get(i);
				empty = false;
			}
		}
		if(bConsequentIdx)
			formula += " => " + path.items.get(idx);
		
		if(!precondition.equals(""))
			formula += ")";
		
		return formula;
	}
	
	public Vector<String> getFormulas(boolean calculateWeights, boolean addReverseImplications) {
		Vector<String> formulas = new Vector<String>();
		Iterator<Path> iPaths = paths.iterator();
		while(iPaths.hasNext()) {
			Path path = iPaths.next();
			String prefix = "";
			if(calculateWeights) {
				double prob = path.accuracy * path.support + (1-path.support);
				if(prob == 1.0)
					prob = 0.999999;
				double weight = Math.log(prob/(1-prob));
				prefix = String.format("%.6f  ", weight);
			}
			formulas.add(prefix + createImplication(path, path.items.size()-1, true));
			if(!calculateWeights && addReverseImplications)
				formulas.add(createImplication(path, path.items.size()-1, false));
		}
		return formulas;
	}
	
	/**
	 * Class that represents an object type and associated information relative to a core object
	 * @author Dominik Jain
	 *
	 */
	public class ObjectType {
		public String objName, varName;
		protected String precondition;
		protected ObjectType[] referencedObjects;
		
		/**
		 * @param objName	the name of the object to which a relation exists
		 * @param varName	the variable name that should be used for this object in the logical formulas that are generated
		 * @param precondition	a precondition/precondition (logical formula) that links the core object to the related object; may be null 
		 * @param referencedRelations	an array of other object types of which instances need to exist as an additional precondition, i.e. the precoditions of referenced object need to hold as well (recursion). This argument may be null if no other objects are referenced.		 
		 */
		ObjectType(String objName, String varName, String precondition, ObjectType[] referencedRelations) {
			this.objName = objName;
			this.varName = varName;
			this.precondition = precondition;
			this.referencedObjects = referencedRelations;
		}
		
		public String getPrecondition() {
			return getPrecondition(new HashSet<ObjectType>());
		}

		public String getPrecondition(Set<ObjectType> excludedReferences) {
			String ret = "";
			int preconditions = 0;
			if(precondition != null) { 
				ret = precondition;
				preconditions++;
			}
			if(referencedObjects != null) {
				for(int i = 0; i < referencedObjects.length; i++) {
					if(!excludedReferences.contains(referencedObjects[i])) {
						if(preconditions > 0)
							ret += " ^ ";
						ret += referencedObjects[i].getPrecondition(excludedReferences);	
					}						
				}					
			}
			return ret;
		}
		
		public void setVarName(String varName) {
			this.varName = varName;
		}
	}
	
	public ObjectType setObjectType(String objName, String varName, String precondition, ObjectType[] referencedRelations) {
		ObjectType rel = new ObjectType(objName, varName, precondition, referencedRelations);
		objects.put(objName, rel);
		return rel;
	}
	
	public class RelationType {
		public ObjectType from, to;
		public RelationType(ObjectType from, ObjectType to) {
			this.to = to;
			this.from = from;
		}
	}

	/**
	 * tells the converter that the item with the given name (in the proximity subgraph), which may have attributes that are used in the classification using RPTs, is a link -- not an object -- and that it represents the given relation.
	 * @param itemName	the name of the item
	 * @param from	the object where the (directed) link originates
	 * @param to the object the link leads to
	 */
	public void setRelationType(String itemName, ObjectType from, ObjectType to) {
		relations.put(itemName, new RelationType(from, to));
	}
	
	public void setPrecondition(String precond) {
		this.precondition = precond;
	}
}
