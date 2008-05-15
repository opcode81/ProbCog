package edu.tum.cs.logic;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.logic.parser.FormulaParser;
import edu.tum.cs.logic.parser.ParseException;

/**
 * class that represents a logical knowledge base
 * @author jain
 *
 */
public class KnowledgeBase implements Iterable<Formula> {
	protected Vector<Formula> formulas;
	
	public KnowledgeBase() {
		formulas = new Vector<Formula>();
	}
	
	/**
	 * constructor that reads a number of .-terminated formula statements from a file
	 * @param filename
	 * @throws IOException
	 * @throws ParseException
	 */
	public KnowledgeBase(String filename) throws IOException, ParseException {
		this();
		// TODO do this properly, skipping the comments
		BufferedReader br = new BufferedReader(new FileReader(new java.io.File(filename)));
		String line;
		for(;;) {
			line = br.readLine();
			if(line == null)
				break;
			if(line.endsWith(".") && !line.startsWith("/"))
				formulas.add(FormulaParser.parse(line.substring(0, line.length()-1)));
		}
	}
	
	public Vector<Formula> getFormulas() {
		return formulas;
	}
	
	public KnowledgeBase ground(Database db, WorldVariables worldVars) throws Exception {
		KnowledgeBase ret = new KnowledgeBase();
		for(Formula f : formulas) {
			f.addAllGroundingsTo(ret.formulas, db, worldVars);
		}
		return ret;
	}

	public Iterator<Formula> iterator() {
		return formulas.iterator();
	}
	
	public int size() {
		return formulas.size();
	}
}
