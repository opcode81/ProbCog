package edu.tum.cs.srl.bayesnets.bln;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.KnowledgeBase;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.MLNConverter;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;

public class BayesianLogicNetwork extends AbstractBayesianLogicNetwork {

	public KnowledgeBase kb;
	
	public BayesianLogicNetwork(String declsFile, String fragmentsFile, String logicFile) throws Exception {
		super(declsFile, fragmentsFile, logicFile);
	}
	
	public BayesianLogicNetwork(String declsFile, String fragmentsFile) throws Exception {
		super(declsFile, fragmentsFile, null);
	}

	public BayesianLogicNetwork(String declsFile) throws Exception {
		super(declsFile);				
	}
	
	public MarkovLogicNetwork toMLN() throws Exception {
		MLNConverter.MLNObjectWriter converter = new MLNConverter.MLNObjectWriter();
		this.rbn.toMLN(converter, false, false);
		for(Formula f : kb) {
			converter.addHardFormula(f);
		}
		return converter.getMLN();
	}

	@Override
	public GroundBLN ground(Database db) throws Exception {
		GroundBLN gbln = new GroundBLN(this, db);
		this.paramHandler.addSubhandler(gbln);
		return gbln;
	}
	
	@Override 
	public void initKB() throws Exception {
		kb = new KnowledgeBase();
		if(logicFile != null)
			kb.readFile(logicFile.toString());
	}
}