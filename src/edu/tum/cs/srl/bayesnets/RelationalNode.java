package edu.tum.cs.srl.bayesnets;

import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.mln.MLNWriter;
import edu.tum.cs.tools.Pair;
import edu.tum.cs.tools.StringTool;

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
	 * noisy-or parameters, i.e. parameters that are free in some parents (which must consequently be grounded in an auxiliary parent node, and all aux. parents must be combined via noisy-or)
	 */
	public String[] addParams;
	public boolean isConstant, isAuxiliary, isPrecondition, isUnobserved;
	public String parentMode, aggregator;
	
	public static final String BUILTINPRED_EQUALS = "EQ";
	public static final String BUILTINPRED_NEQUALS = "NEQ";
	
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
		Pattern aggPat = Pattern.compile("(=?[A-Z]+):.*");
		Matcher m = aggPat.matcher(name);
		if(m.matches()) {
			aggregator = m.group(1);
			name = name.substring(aggregator.length()+1);
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
		
		if(isPrecondition)
			bn.setEvidenceFunction(functionName);
	}
	
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
			return bn.isBooleanDomain(node.getDomain());
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
	protected String toLiteral(int setting, HashMap<String,String> constantValues) {		
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
	 * gets the network this node belongs to
	 */
	public RelationalBeliefNetwork getNetwork() {
		return bn;
	}
	
	/**
	 * @return true if this node is a noisy or node, i.e. a node where the probability value is computed using a noisy disjunctive combination of the probability values of its parents  
	 */
	public boolean isNoisyOr() {
		return aggregator != null && aggregator.equals("OR");
	}
	
	/**
	 * @return true if the node has a conditional probability distribution given as a CPT
	 */
	public boolean hasCPT() {
		return !isNoisyOr();
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
		if(this.aggregator != null && this.aggregator.length() > 0)
			buf.append(aggregator + ":");
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
	public String getValueInDB(String[] actualParams, Database db, boolean closedWorld) throws Exception {
		// ** special built-in predicate
		if(functionName.equals(BUILTINPRED_NEQUALS))
			return actualParams[0].equals(actualParams[1]) ? "False" : "True";
		if(functionName.equals(BUILTINPRED_EQUALS))
			return actualParams[0].equals(actualParams[1]) ? "True" : "False";
		// ** regular predicate/constant
		if(!isConstant) { // if the node is not a constant node, we can obtain its value by performing a database lookup
			String curVarName = getVariableName(actualParams);
			// set value
			String value = db.getVariableValue(curVarName, closedWorld);
			if(value == null) {
				throw new Exception("Could not find value of " + curVarName + " in database. closedWorld = " + closedWorld);
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
}

