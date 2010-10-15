package edu.tum.cs.srl.bayesnets;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.RelationKey;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.learning.CPTLearner;
import edu.tum.cs.srl.bayesnets.learning.DomainLearner;
import edu.tum.cs.srl.taxonomy.Concept;
import edu.tum.cs.srl.taxonomy.Taxonomy;
import edu.tum.cs.util.StringTool;

/**
 * Advanced Bayesian Logical (ABL) Model
 * 
 * @author jain
 */
public class ABL extends BLOGModel {

	public ABL(String[] blogFiles, String networkFile) throws Exception {
		super(blogFiles, networkFile);
	}

	public ABL(String blogFile, String networkFile) throws Exception {
		this(new String[] { blogFile }, networkFile);
	}

	@Override
	protected boolean readDeclaration(String line) throws Exception {
		if (super.readDeclaration(line))
			return true;
		// read functional dependencies among relation arguments
		if (line.startsWith("relationKey") || line.startsWith("RelationKey")) {
			Pattern pat = Pattern
					.compile("[Rr]elationKey\\s+(\\w+)\\s*\\((.*)\\)\\s*;?");
			Matcher matcher = pat.matcher(line);
			if (matcher.matches()) {
				String relation = matcher.group(1);
				String[] arguments = matcher.group(2).trim().split("\\s*,\\s*");
				addRelationKey(new RelationKey(relation, arguments));
				return true;
			}
			return false;
		}
		// read type information
		if (line.startsWith("type") || line.startsWith("Type")) {
			if (taxonomy == null)
				taxonomy = new Taxonomy();
			Pattern pat = Pattern.compile("[Tt]ype\\s+(.*?);?");
			Matcher matcher = pat.matcher(line);
			Pattern typeDecl = Pattern.compile("(\\w+)(?:\\s+isa\\s+(\\w+))?");
			if (matcher.matches()) {
				String[] decls = matcher.group(1).split("\\s*,\\s*");
				for (String d : decls) {
					Matcher m = typeDecl.matcher(d);
					if (m.matches()) {
						Concept c = new Concept(m.group(1));
						taxonomy.addConcept(c);
						if (m.group(2) != null) {
							Concept parent = taxonomy.getConcept(m.group(2));
							if (parent == null)
								throw new Exception(
										"Error in declaration of type '"
												+ m.group(1)
												+ "': The parent type '"
												+ m.group(2)
												+ "' is undeclared.");
							c.setParent(parent);
						}
						return true;
					} else
						throw new Exception("The type declaration '" + d
								+ "' is invalid");
				}
			}
			return false;
		}
		// prolog rule
		if (line.startsWith("prolog")) {
			String rule = line.substring(6).trim();
			if (!rule.endsWith("."))
				rule += ".";
			prologRules.add(rule);
			return true;
		}
		// combining rule
		if(line.startsWith("combining-rule")) {
			Pattern pat = Pattern.compile("combining-rule\\s+(\\w+)\\s+([-\\w]+)\\s*;?");
			Matcher matcher = pat.matcher(line);
			if(matcher.matches()) {
				String function = matcher.group(1);
				String strRule = matcher.group(2);
				Signature sig = getSignature(function);
				CombiningRule rule;
				if(sig == null) 
					throw new Exception("Defined combining rule for unknown function '" + function + "'");
				try {
					rule = CombiningRule.fromString(strRule);
				}
				catch(IllegalArgumentException e) {
					Vector<String> v = new Vector<String>();
					for(CombiningRule cr : CombiningRule.values()) 
						v.add(cr.stringRepresention);
					throw new Exception("Invalid combining rule '" + strRule + "'; valid options: " + StringTool.join(", ", v));
				}
				this.combiningRules.put(function, rule);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void writeDeclarations(PrintStream out) {
		super.writeDeclarations(out);

		// write relation keys
		for (Collection<RelationKey> ckey : this.relationKeys.values()) {
			for (RelationKey key : ckey) {
				out.println("relationKey " + key.toString());
			}
		}
		out.println();
	}

	public static void main(String[] args) {
		try {
			String bifFile = "abl/kitchen-places/actseq.xml";
			ABL bn = new ABL(new String[] { "abl/kitchen-places/actseq.abl" },
					bifFile);
			String dbFile = "abl/kitchen-places/train.blogdb";
			// read the training database
			System.out.println("Reading data...");
			Database db = new Database(bn);
			db.readBLOGDB(dbFile);
			System.out.println("  " + db.getEntries().size()
					+ " variables read.");
			// learn domains
			if (true) {
				System.out.println("Learning domains...");
				DomainLearner domLearner = new DomainLearner(bn);
				domLearner.learn(db);
				domLearner.finish();
			}
			// learn parameters
			System.out.println("Learning parameters...");
			CPTLearner cptLearner = new CPTLearner(bn);
			cptLearner.learnTyped(db, true, true);
			cptLearner.finish();
			System.out.println("Writing XML-BIF output...");
			bn.saveXMLBIF(bifFile);
			if (true) {
				System.out.println("Showing Bayesian network...");
				bn.show();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void write(PrintStream out) throws Exception {
		super.writeDeclarations(out);
	}
}
