package edu.tum.cs.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.logic.parser.FormulaParser;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.tools.FileUtil;

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
				formulas.add(FormulaParser.parse(line.substring(0, line.length()-1)));
			else
				System.err.println("Warning: Line without terminating period ignored: " + line);
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
