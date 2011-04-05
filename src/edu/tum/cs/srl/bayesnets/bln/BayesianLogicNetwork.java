package edu.tum.cs.srl.bayesnets.bln;

import java.io.File;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.KnowledgeBase;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.MLNConverter;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.util.FileUtil;

public class BayesianLogicNetwork extends AbstractBayesianLogicNetwork {

	public KnowledgeBase kb;
	
	public BayesianLogicNetwork(String declsFile, String fragmentsFile, String logicFile) throws Exception {
		super(declsFile, fragmentsFile, logicFile);
		kb = new KnowledgeBase(logicFile);
	}

	public BayesianLogicNetwork(File declsFile) throws Exception {
		this(FileUtil.readTextFile(declsFile));				
	}
	
	public BayesianLogicNetwork(String decls) throws Exception {
		super(decls);
	}
	
	@Override
	protected boolean readDeclaration(String line) throws Exception {
		if(super.readDeclaration(line))
			return true;
		
		return false;
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
		return new GroundBLN(this, db);
	}
}
