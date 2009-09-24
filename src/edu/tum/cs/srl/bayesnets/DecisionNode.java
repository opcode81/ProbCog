package edu.tum.cs.srl.bayesnets;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.IPossibleWorld;
import edu.tum.cs.logic.PossibleWorldFromDatabase;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.parser.FormulaParser;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.Database;

public class DecisionNode extends ExtendedNode {
	protected boolean isOperator;
	protected enum Operator {
		Negation;
	};
	protected Operator operator;
	protected Formula formula;
	
	public DecisionNode(RelationalBeliefNetwork rbn, edu.ksu.cis.bnj.ver3.core.BeliefNode node) throws ParseException {
		super(rbn, node);
		// check if the node is an operator that is to be applied to its parents, which are also decision nodes
		operator = null;
		if(node.getName().equals("neg")) {				
			operator = Operator.Negation; 
		}
		// if it's not an operator, it's a formula which we have to parse
		if(operator == null) {
			formula = FormulaParser.parse(node.getName());
		}
	}
	
	/**
	 * returns the truth value of the formula that corresponds to this decision node 
	 * @param varBinding	variable binding that applies (as given by the actual parameters of this decision node's child/descendant)
	 * @param w				a possible world specifying truth values for all variables (ground atoms)
	 * @param worldVars		a set of world variables to take ground atom instances from (for grounding the formula)		
	 * @param db			a database to take objects from for existential quantification
	 * @return	true if the formula is satisfied
	 * @throws Exception
	 */
	public boolean isTrue(Map<String, String> varBinding, IPossibleWorld w, WorldVariables worldVars, Database db) throws Exception {
		if(operator != null) {
			Collection<DecisionNode> parents = this.getDecisionParents();
			switch(operator) {
			case Negation:
				if(parents.size() != 1)
					throw new Exception("Operator neg must have exactly one child");
				return !parents.iterator().next().isTrue(varBinding, w, worldVars, db);		
			default:
				throw new Exception("Operator not handled");
			}
		}
		else {
			try {
				Formula gf = formula.ground(varBinding, worldVars, db);
				return gf.isTrue(w);				
			}
			catch(Exception e) {
				throw new Exception("Cannot evaluate precondition " + formula + ": " + e.getMessage());
			}
		}
	}
	
	/**
	 * a wrapper for the other implementation of isTrue that uses the possible world implied by the database to determine the truth values of ground atoms
	 * @param paramNames		variables that are bound
	 * @param actualParams		values the variables are bound to
	 * @param db				the database that provides the truth values for all ground atoms (closed-world assumption)
	 * @param closedWorld		whether to make the closed-world assumption (i.e. that atoms not specified in the database are false)
	 * @return
	 * @throws Exception 
	 */
	public boolean isTrue(String[] paramNames, String[] actualParams, Database db, boolean closedWorld) throws Exception {
		// generate variable bindings
		HashMap<String, String> varBinding = new HashMap<String, String>();
		for(int i = 0; i < paramNames.length; i++)
			varBinding.put(paramNames[i], actualParams[i]);
		// construct a dummy collection of world variables that can be used to obtain ground atoms for ground formulas
		WorldVariables worldVars = new WorldVariables() { 
			@Override
			public GroundAtom get(String gndAtom) {
				return new GroundAtom(gndAtom); // since we don't need indexed ground atoms, we can just construct them on demand
			}
		};
		// call other implementation
		return isTrue(varBinding, new PossibleWorldFromDatabase(this.bn, db, closedWorld), worldVars, db);
	}
}
