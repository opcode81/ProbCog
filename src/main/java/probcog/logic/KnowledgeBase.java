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
package probcog.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.exception.ProbCogException;
import probcog.logic.Formula.FormulaSimplification;
import probcog.logic.parser.FormulaParser;
import probcog.logic.parser.ParseException;
import probcog.srl.Database;

import edu.tum.cs.util.FileUtil;

/**
 * Represents a logical knowledge base
 * @author Dominik Jain
 */
public class KnowledgeBase implements Iterable<Formula> {
	protected Vector<Formula> formulas;
	/**
	 * stores, for ground KBs, the index of the original formula from which the formula was instantiated
	 */
	protected HashMap<Formula, Integer> templateIDs;
	
	public KnowledgeBase() {
		formulas = new Vector<Formula>();
		templateIDs = new HashMap<Formula, Integer>();
	}
	
	/**
	 * constructor that reads a number of .-terminated formula statements from a file
	 * @param filename
	 * @throws ProbCogException 
	 */
	public KnowledgeBase(String filename) throws ProbCogException {
		this();
		readFile(filename);
	}	
	
	public void readFile(String filename) throws ProbCogException {
		try {
			// read KB file
			String fileContent = FileUtil.readTextFile(filename);
			// remove comments
			Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
			Matcher matcher = comments.matcher(fileContent);
			fileContent = matcher.replaceAll("");
			// read lines
			BufferedReader br = new BufferedReader(new StringReader(fileContent));
			String line;
			for(;;) {
				line = br.readLine();
				if(line == null)
					break;
				line = line.trim();
				if(line.length() == 0)
					continue;
				if(line.endsWith("."))
					addFormula(FormulaParser.parse(line.substring(0, line.length()-1)));
				else
					System.err.println("Warning: Line without terminating period ignored: " + line);
			}		
		}
		catch (IOException|ParseException e) {
			throw new ProbCogException(e);
		}
	}
	
	public void addFormula(Formula f) {
		this.formulas.add(f);
	}
	
	public Vector<Formula> getFormulas() {
		return formulas;
	}

	/**
	 * grounds this knowledge base (using a set of entities and the corresponding set of ground atoms)
	 * @param db
	 * @param worldVars the set of ground atoms
	 * @param simplify whether to use the evidence in the database to simplify ground formulas
	 * @return
	 * @throws ProbCogException
	 */
	public KnowledgeBase ground(Database db, WorldVariables worldVars, FormulaSimplification simplify) throws ProbCogException {
		KnowledgeBase ret = new KnowledgeBase();
		Integer formulaID = 0;
		for(Formula f : formulas) {
			int i = ret.formulas.size();
			f.addAllGroundingsTo(ret.formulas, db, worldVars, simplify);
			for(; i < ret.formulas.size(); i++) {
				ret.templateIDs.put(ret.formulas.get(i), formulaID);
			}
			formulaID++;
		}
		return ret;
	}

	public Iterator<Formula> iterator() {
		return formulas.iterator();
	}
	
	public int size() {
		return formulas.size();
	}
	
	public Integer getTemplateID(Formula f) {
		return templateIDs.get(f);
	}
}
