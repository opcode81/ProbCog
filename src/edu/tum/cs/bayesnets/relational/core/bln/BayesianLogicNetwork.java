package edu.tum.cs.bayesnets.relational.core.bln;

import java.io.IOException;

import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;
import edu.tum.cs.logic.KnowledgeBase;
import edu.tum.cs.logic.parser.ParseException;

public class BayesianLogicNetwork extends AbstractBayesianLogicNetwork {

	public KnowledgeBase kb;
	
	public BayesianLogicNetwork(RelationalBeliefNetwork rbn, String logicFile) throws IOException, ParseException {
		super(rbn, logicFile);
		kb = new KnowledgeBase(logicFile);
	}

}
