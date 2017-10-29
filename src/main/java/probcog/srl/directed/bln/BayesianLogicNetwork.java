/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.directed.bln;

import probcog.logic.Formula;
import probcog.logic.KnowledgeBase;
import probcog.logic.parser.FormulaParser;
import probcog.logic.parser.ParseException;
import probcog.srl.Database;
import probcog.srl.directed.MLNConverter;
import probcog.srl.mln.MarkovLogicNetwork;

/**
 * Represents a Bayesian Logic Network.
 * @author Dominik Jain
 */
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
