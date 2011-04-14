package edu.tum.cs.srl.bayesnets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.logic.Atom;
import edu.tum.cs.logic.Biimplication;
import edu.tum.cs.logic.Conjunction;
import edu.tum.cs.logic.Equality;
import edu.tum.cs.logic.Exist;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.Literal;
import edu.tum.cs.logic.Negation;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.GenericDatabase;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.mln.MLNWriter;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;

public class RelationalNode extends ExtendedNode {
	/**
	 * the function/predicate name this node is concerned with (without any arguments)
	 */
	protected String functionName;
	/**
	 * the list of node parameters
	 */
	public String[] params;
	/**
	 * additional parameters that are free in some parents, necessitating the use of an aggregator
	 */
	public String[] addParams;
	/**
	 * collection of indices of parameters that are constants rather than variables
	 */
	public Vector<Integer> constantParamIndices = new Vector<Integer>();
	public boolean isConstant, isAuxiliary, isPrecondition, isUnobserved;
	/**
	 * specification of an aggregation to handle a variable number of parent sets
	 */
	public Aggregator aggregator;
	/**
	 * an additional parameterization of the aggregation method
	 */
	public String parentMode;
	protected Vector<Integer> indicesOfConstantArgs = null;
	/**
	 * (for constant nodes only, i.e. if isConstant is true) the return type of the variable
	 */
	String constantType;
	
	/**
	 * a parent grounder used to instantiate variables (which is created on demand)
	 */
	protected ParentGrounder parentGrounder = null;
	
	public static final String BUILTINPRED_EQUALS = "EQ";
	public static final String BUILTINPRED_NEQUALS = "NEQ";
	
	public static enum Aggregator {
		FunctionalOr(true, "=OR"),
		NoisyOr(false, "OR"),
		Average(false, "AVG");
		
		public boolean isFunctional;
		protected String syntax;
		
		private Aggregator(boolean isFunctional, String syntax) {		
			this.isFunctional = isFunctional;
			this.syntax = syntax;
		}
		
		public String toString() {
			return super.toString() + "(\"" + syntax + "\")";		
		}
		
		public String getFunctionSyntax() {
			return syntax;
		}
		
		public static Aggregator fromSyntax(String syntax) throws Exception {
			for(Aggregator a : Aggregator.values())
				if(a.syntax.equals(syntax))
					return a;
			throw new Exception("There is no aggregator for '" + syntax + "'");
		}
	}
	
	
	/**
	 * extracts the node name (function/predicate name) from a variable name (which contains arguments)
	 * @param varName
	 * @return
	 */
	public static String extractFunctionName(String varName) {
		if(varName.contains("("))
			return varName.substring(0, varName.indexOf('('));
		return varName;
		
	}
	
	public static Pair<String, String[]> parse(String variable) {
		Pattern p = Pattern.compile("(\\w+)\\(([^\\)]+)\\)");
		Matcher m = p.matcher(variable);
		if(!m.matches())
			return null;
		return new Pair<String, String[]>(m.group(1), m.group(2).split(","));
	}
	
	/**
	 * @return true if the given identifier is a constant name, false otherwise
	 * @param identifier
	 */
	public static boolean isConstant(String identifier) {
		return Character.isUpperCase(identifier.charAt(0));
	}
	
	public RelationalNode(RelationalBeliefNetwork bn, BeliefNode node) throws Exception {
		super(bn, node);
		Pattern namePat = Pattern.compile("(\\w+)\\((.*)\\)");
		String name = node.getName();
		// preprocessing: special parent nodes encoded in prefix 
		if(name.charAt(0) == '#') { // auxiliary: CPT is meaningless
			isAuxiliary = true;
			name = name.substring(1);
		}
		else if(name.charAt(0) == '+') { // precondition: node is boolean and required to be true
			isPrecondition = true;			
			isAuxiliary = true;
			name = name.substring(1);
		}
		// preprocessing: special child node that has a variable number of parents
		// - aggregator as prefix
		aggregator = null;
		Pattern aggPat = Pattern.compile("(=?[A-Z]+):.*");
		Matcher m = aggPat.matcher(name);
		if(m.matches()) {
			String aggFunction = m.group(1);
			aggregator = Aggregator.fromSyntax(aggFunction);
			name = name.substring(aggFunction.length()+1);
		}
		// - free variables and how they are treated as postfix
		int sepPos = name.indexOf('|');
		if(sepPos != -1) {
			String decl = name.substring(sepPos+1);
			Pattern declPat = Pattern.compile("([A-Z]+):(.*)");			
			m = declPat.matcher(decl);
			if(m.matches()) {
				parentMode = m.group(1);
				addParams = m.group(2).split("\\s*,\\s*");
			}
			else { // deprecated
				addParams = decl.split("\\s*,\\s*");
			}
			name = name.substring(0, sepPos);
		}
		// match function and parameters
		Matcher matcher = namePat.matcher(name);
		if(matcher.matches()) {	// a proper relational node, such as "foo(x,y)"
			this.functionName = matcher.group(1);
			this.params = matcher.group(2).split("\\s*,\\s*");
			this.isConstant = false;
		}
		else { // constant: usually a node such as "x"
			this.functionName = name;
			this.params = new String[]{name};
			this.isConstant = true;
		}
		
		// check if any parameters are not variables but constants
		for(int i = 0; i < params.length; i++)
			if(isConstant(params[i]))
				constantParamIndices.add(i);
		
		if(isPrecondition)
			bn.setEvidenceFunction(functionName);
	}
	
	/**
	 * determines whether this node corresponds to a fragment variable
	 * @return true if the node corresponds to a fragment
	 */
	public boolean isFragment() {
		return !isConstant && !isAuxiliary && !isBuiltInPred();
	}
	
	/**
	 * @return the full name/label of this node
	 */
	public String toString() {
		return getName();		
	}
	
	/**
	 * gets the full name/label of this node
	 * @return
	 */
	public String getName() {
		return this.node.getName();
	}
	
	/**
	 * gets the index of the corresponding belief node in the RBN
	 * @return
	 */
	public int getNodeIndex() {
		return this.index;
	}
	
	/**
	 * gets the clean name of this node (the label without prefixes or suffixes), i.e. only the predicate and its parameters
	 * @return
	 */
	public String getCleanName() {
		if(isConstant)
			return functionName;
		return Signature.formatVarName(this.functionName, this.params);
	}
	
	/**
	 * @return true if the node node is boolean, i.e. it has a boolean domain
	 */
	public boolean isBoolean() {
		Signature sig = bn.getSignature(this);
		if(sig != null)
			return sig.isBoolean();
		else
			return RelationalBeliefNetwork.isBooleanDomain(node.getDomain());
	}
	
	public String getReturnType() {
		if(isConstant)
			return constantType;
		else {
			Signature sig = getSignature();
			if(sig == null) 
				return null;
			return sig.returnType;
		}
	}
	
	/**
	 * gets the name of the function/predicate that this node corresponds to
	 * @return
	 */
	public String getFunctionName() {
		return this.functionName;
	}
	
	/**
	 * generates a textual representation of the logical literal that this node represents for a certain assignment (and, optionally, substitutions of its parameters) 
	 * @param setting  the value this node is set to given by an index into the node's domain
	 * @param constantValues  mapping of this node's arguments to constants; any subset/superset of arguments may be mapped; may be null
	 * @return
	 */
	public String toLiteralString(int setting, HashMap<String,String> constantValues) {		
		// ** special built-in predicate with special logical translation
		if(this.functionName.equals(BUILTINPRED_NEQUALS))
			return String.format("!(%s=%s)", this.params[0], this.params[1]);
		if(this.functionName.equals(BUILTINPRED_EQUALS))
			return String.format("%s=%s", this.params[0], this.params[1]);
		
		// ** regular predicate
		// predicate name
		StringBuffer sb = new StringBuffer(String.format("%s(", MLNWriter.lowerCaseString(functionName)));
		// add parameters
		for(int i = 0; i < params.length; i++) {
			if(i > 0)
				sb.append(",");
			String value = constantValues != null ? constantValues.get(params[i]) : null;
			if(value == null)
				sb.append(params[i]);
			else
				sb.append(value);
		}
		// add node value (negation as prefix or value of non-boolean variable as additional parameter)
		String value = ((Discrete)node.getDomain()).getName(setting);
		if(isBoolean()) {
			if(value.equalsIgnoreCase("false"))
				sb.insert(0, '!');
		}
		else {
			sb.append(',');
			sb.append(MLNWriter.upperCaseString(value));			
		}
		sb.append(')');
		return sb.toString();
	}
	
	/**
	 * returns a logical representation of the semantics of this node (only applicable to nodes with aggregators!)
	 * @param constantValues a mapping of constant parents of this node to values (may be null)
	 * @return a formula that corresponds to the semantics of this node or null if no translation could be made
	 * @throws Exception 
	 */
	public Formula toFormula(Map<String,String> constantValues) throws Exception {
		if(!hasAggregator())
			return null;
		if(aggregator == Aggregator.FunctionalOr) {
			// this <=> exist parameters: conjunction of parents
			Vector<Formula> parents = new Vector<Formula>();
			for(RelationalNode parent : this.getRelationalParents()) {
				parents.add(parent.toLiteral(0, constantValues)); // 0=true					
			}
			return new Biimplication(this.toLiteral(0, constantValues), new Exist(this.addParams, new Conjunction(parents)));
		}
		return null;
	}
	
	/**
	 * generates a logical representation of what it means to set this node to the given domain index
	 * @param domIdx
	 * @param constantValues
	 * @return a logical formula (e.g. literal or (negated) equality statement)
	 */
	public Formula toLiteral(int domIdx, Map<String,String> constantValues) {
		// ** special built-in predicate with special logical translation
		if(this.functionName.equals(BUILTINPRED_NEQUALS))
			return new Negation(new Equality(this.params[0], this.params[1]));
		if(this.functionName.equals(BUILTINPRED_EQUALS))
			return new Equality(this.params[0], this.params[1]);
		
		// ** regular predicate
		Vector<String> atomParams = new Vector<String>();
		for(int i = 0; i < params.length; i++) {
			String value = constantValues != null ? constantValues.get(params[i]) : null;
			if(value == null)
				atomParams.add(params[i]);
			else
				atomParams.add(value);
		}
		// add node value (negation as prefix or value of non-boolean variable as additional parameter)
		String value = ((Discrete)node.getDomain()).getName(domIdx);
		if(isBoolean()) {
			boolean isTrue = !value.equalsIgnoreCase("false");
			return new Literal(isTrue, new Atom(this.functionName, atomParams));
		}
		else {
			atomParams.add(value);
			return new Atom(this.functionName, atomParams);
		}		
	}
	
	/**
	 * gets the network this node belongs to
	 */
	public RelationalBeliefNetwork getNetwork() {
		return bn;
	}
	
	/**
	 * @return true if the node has a conditional probability distribution given as a CPT
	 */
	public boolean hasCPT() {
		return aggregator == null || !aggregator.isFunctional;
	}
	
	/**
	 * @return true iff this node has a combination function (i.e. an aggregator) assigned to it
	 */
	public boolean hasAggregator() {
		return this.aggregator != null;
	}
	
	/**
	 * retrieves this node's signature
	 * @return
	 */
	public Signature getSignature() {
		return bn.getSignature(this);
	}
	
	/**
	 * @return true if the node represents a relation between two or more objects
	 */
	public boolean isRelation() {
		return params != null && params.length > 1;
	}
	
	
	/**
	 * gets the name of the variable (grounded node) that results when applying the given actual parameters to this node 
	 * @param actualParams
	 * @return
	 * @throws Exception 
	 */
	public String getVariableName(String[] actualParams) throws Exception {
		if(actualParams.length != params.length)
			throw new Exception(String.format("Invalid number of actual parameters suppplied for %s: expected %d, got %d", toString(), params.length, actualParams.length));
		return Signature.formatVarName(getFunctionName(), actualParams);
	}
	
	public Vector<RelationalNode> getParents() {
		return bn.getRelationalParents(this);
	}
	
	/**
	 * 
	 * @param params
	 * @return true if the node has all of the given parameters
	 */
	public boolean hasParams(String[] params) {
		for(int i = 0; i < params.length; i++) {
			int j = 0;
			for(; j < this.params.length; j++)
				if(params[i].equals(this.params[j]))
					break;
			if(j == this.params.length)
				return false;
		}
		return true;
	}
	
	public boolean hasParam(String param) {
		for(int i = 0; i < params.length; i++)
			if(params[i].equals(param))
				return true;
		return false;
	}
	
	/**
	 * gets the node (which must be a relation) that grounds the free parameters of this node (applicable only to nodes that have free parameters)
	 * @return 
	 * @throws Exception
	 */
	public RelationalNode getFreeParamGroundingParent() throws Exception {
		if(addParams == null || addParams.length == 0)
			throw new Exception("This node has no free parameters for which there could be a parent that grounds them.");
		// find the parent that grounds the free parameters: It must be a relation which contains all of the free params
		for(RelationalNode parent : getParents()) {
			if(parent.isRelation() && parent.hasParams(this.addParams)) {
				return parent;
			}
		}
		return null;
	}
	
	/**
	 * @deprecated use toLiteralString
	 * @return
	 * @throws Exception
	 */
	public String toAtom() throws Exception {
		if(!isBoolean())
			throw new Exception("Cannot convert non-Boolean node to atom without specifying setting");
		return getCleanName();
	}
	
	/**
	 * changes the node label to reflect the internal status of this node
	 */
	public void setLabel() {
		StringBuffer buf = new StringBuffer();
		if(this.aggregator != null)
			buf.append(aggregator.getFunctionSyntax() + ":");
		buf.append(getCleanName());
		if(this.addParams != null && this.addParams.length > 0) {
			buf.append("|");
			if(this.parentMode != null && this.parentMode.length() > 0)
				buf.append(parentMode + ":");
			buf.append(StringTool.join(",", this.addParams));
		}
		this.node.setName(buf.toString());
	}
	
	public Discrete getDomain() {
		return (Discrete)node.getDomain();
	}
	
	/**
	 * gets the value of this node for a specific setting of its parameters given a specific database
	 * @param paramSets
	 * @param db
	 * @return
	 * @throws Exception 
	 */
	public String getValueInDB(String[] actualParams, GenericDatabase<?,?> db, boolean closedWorld) throws Exception {
		// ** special built-in predicate
		if(functionName.equals(BUILTINPRED_NEQUALS))
			return actualParams[0].equals(actualParams[1]) ? "False" : "True";
		if(functionName.equals(BUILTINPRED_EQUALS))
			return actualParams[0].equals(actualParams[1]) ? "True" : "False";
		// ** regular predicate/constant
		if(!isConstant) { // if the node is not a constant node, we can obtain its value by performing a database lookup
			String curVarName = getVariableName(actualParams);
			// set value
			String value = db.getSingleVariableValue(curVarName, closedWorld);						
			if(value == null) {
				throw new Exception("Could not find the unique value of " + curVarName + " in database. closedWorld = " + closedWorld);
			}
			return value;
			//System.out.println("For " + varName + ": " + curVarName + " = " + value);
		}
		else { // the current node is does not correspond to an atom/predicate but is a constant that appears in the argument list of the main node
			// the value of the current node is given directly as one of the main node's parameters, which has been grounded as this node's actual parameter
			return actualParams[0];
		}
	}	
	
	public boolean isBuiltInPred() {
		return functionName.equals(BUILTINPRED_EQUALS) || functionName.equals(BUILTINPRED_NEQUALS);
	}
	
	/**
	 * gets a collection of possible constant assignments (i.e. assignments to parents of this node that are constant nodes)
	 * @return a vector of mappings from constant name to value
	 */
	public Vector<HashMap<String,String>> getConstantAssignments() {
		RelationalNode mainNode = this;
		// - so first get a list of parents that are constants
		Vector<RelationalNode> constantParents = new Vector<RelationalNode>();
		for(RelationalNode parent : this.getNetwork().getRelationalParents(mainNode)) {
			if(parent.isConstant)
				constantParents.add(parent);
		}
		// - get a set of possible value assignments to the constant nodes
		Vector<HashMap<String, String>> constantAssignments = new Vector<HashMap<String, String>>();
		if(constantParents.isEmpty())
			constantAssignments.add(new HashMap<String,String>());
		else
			collectConstantAssignments(constantParents.toArray(new RelationalNode[0]), 0, new String[constantParents.size()], constantAssignments);
		return constantAssignments;
	}
	
	protected void collectConstantAssignments(RelationalNode[] constNodes, int i, String[] assignment, Vector<HashMap<String,String>> assignments) {
		if(i == constNodes.length) {
			HashMap<String,String> m = new HashMap<String,String>();
			for(int j = 0; j < assignment.length; j++)
				m.put(constNodes[j].getName(), assignment[j]);
			assignments.add(m);
		}
		else {
			Discrete dom = (Discrete)constNodes[i].node.getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				assignment[i] = dom.getName(j);
				collectConstantAssignments(constNodes, i+1, assignment, assignments);
			}
		}
	}
	
	/**
	 * gets all the parents of this node that are instances of RelationalNode
	 * @return
	 */
	public Vector<RelationalNode> getRelationalParents() {
		return bn.getRelationalParents(this);
	}
	
	/**
	 * gets the (ordered) vector of indices of parameters that correspond to constants (i.e. are grounded by a constant node)
	 * @return
	 */
	public Vector<Integer> getIndicesOfConstantParams() {
		if(indicesOfConstantArgs == null) {
			indicesOfConstantArgs = new Vector<Integer>();
			HashSet<String> constantArgs = new HashSet<String>();
			for(RelationalNode parent : getRelationalParents()) {
				if(parent.isConstant)
					constantArgs.add(parent.functionName);
			}
			for(int i = 0; i < params.length; i++)
				if(constantArgs.contains(params[i]))
					indicesOfConstantArgs.add(i);
		}
		return indicesOfConstantArgs;
	}
	
	public ParentGrounder getParentGrounder() throws Exception {
		if(parentGrounder != null)
			return parentGrounder;
		return (parentGrounder = new ParentGrounder(this.bn, this));
	}
	
	/**
	 * gets a complete parameter binding (including functionally determined parameters needed in paretns) for a given vector of actual parameters for this node 
	 * @param actualParams
	 * @param db an evidence database (containing, e.g. the evidence predicates for functional lookups)
	 * @return
	 * @throws Exception 
	 */
	public HashMap<String,String> getParameterBinding(String[] actualParams, Database db) throws Exception {
		return getParentGrounder().generateParameterBindings(actualParams, db);
	}
	
	public Vector<RelationalNode> getPreconditionParents() {
		Vector<RelationalNode> ret = new Vector<RelationalNode>();
		BeliefNode[] domprod = this.node.getCPF().getDomainProduct();
		for(int i = 1; i < domprod.length; i++) {
			ExtendedNode n = this.bn.getExtendedNode(domprod[i]);
			if(n instanceof RelationalNode) {
				RelationalNode rn = (RelationalNode)n;
				if(rn.isPrecondition)
					ret.add(rn);
			}			
		}	
		return ret;
	}
	
	public Vector<Map<Integer, String[]>> checkTemplateApplicability(String[] params, Database db) throws Exception {
		RelationalNode relNode = this;
		
		// check constant parameters of this fragment
		for(Integer i : this.constantParamIndices)
			if(!params[i].equals(this.params[i]))
				return null;
		
		// if the node is subject to preconditions (decision node parents), check if they are met
		boolean preconditionsMet = true;
		for(DecisionNode decision : relNode.getDecisionParents()) {					
			if(!decision.isTrue(relNode.params, params, db, false)) {
				preconditionsMet = false;
				break;
			}
		}
		if(!preconditionsMet)
			return null;
		
		// get groundings of parents
		ParentGrounder pg = this.bn.getParentGrounder(relNode);
		Vector<Map<Integer, String[]>> groundings = pg.getGroundings(params, db);
		
		// if there are precondition parents, 
		// filter out the inadmissible parent groundings
		Vector<RelationalNode> preconds = relNode.getPreconditionParents();
		for(RelationalNode precond : preconds) {
			Iterator<Map<Integer, String[]>> iter = groundings.iterator();
			while(iter.hasNext()) {
				Map<Integer, String[]> grounding = iter.next();
				String value = db.getVariableValue(precond.getVariableName(grounding.get(precond.index)), true);
				if(!value.equals("True"))
					iter.remove();
			}
		}
		
		// if there are no groundings left, there is nothing to instantiate
		if(groundings.isEmpty())
			return null;
		
		return groundings;
	}
}

