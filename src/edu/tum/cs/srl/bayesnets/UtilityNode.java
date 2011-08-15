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
import edu.tum.cs.srl.BooleanDomain;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.GenericDatabase;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.RelationalNode.Aggregator;
import edu.tum.cs.srl.mln.MLNWriter;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;

public class UtilityNode extends RelationalNode {

	public UtilityNode(RelationalBeliefNetwork rbn, BeliefNode node) throws Exception {
		super(rbn, node);
	}
	
}
