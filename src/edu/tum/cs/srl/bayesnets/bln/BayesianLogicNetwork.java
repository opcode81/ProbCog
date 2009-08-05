package edu.tum.cs.srl.bayesnets.bln;

import java.io.IOException;

import edu.tum.cs.logic.KnowledgeBase;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;

public class BayesianLogicNetwork extends AbstractBayesianLogicNetwork {

	public KnowledgeBase kb;
	
	public BayesianLogicNetwork(RelationalBeliefNetwork rbn, String logicFile) throws IOException, ParseException {
		super(rbn, logicFile);
		kb = new KnowledgeBase(logicFile);
	}

	public GroundBLN instantiate(Database db) throws Exception {
		GroundBLN gbln = new GroundBLN(this, db);
		gbln.instantiateGroundNetwork();
		return gbln;
	}
}
