package probcog.srl.directed.bln;

import probcog.logic.Formula;
import probcog.logic.KnowledgeBase;
import probcog.logic.parser.FormulaParser;
import probcog.logic.parser.ParseException;
import probcog.srl.Database;
import probcog.srl.directed.MLNConverter;
import probcog.srl.mln.MarkovLogicNetwork;

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
	
	@Override
	public void addLogicalConstraint(String s) throws ParseException {
		kb.addFormula(FormulaParser.parse(s));
	}
}