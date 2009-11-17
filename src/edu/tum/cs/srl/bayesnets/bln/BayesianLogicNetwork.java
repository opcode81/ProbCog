package edu.tum.cs.srl.bayesnets.bln;

import java.io.IOException;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.KnowledgeBase;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.MLNConverter;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;

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
	
	public MarkovLogicNetwork toMLN() throws Exception {
		MLNConverter.MLNObjectWriter converter = new MLNConverter.MLNObjectWriter();
		this.rbn.toMLN(converter, false, false);
		for(Formula f : kb) {
			converter.addHardFormula(f);
		}
		return converter.getMLN();
	}
}
