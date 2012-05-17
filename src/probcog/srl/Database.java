package probcog.srl;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.util.FileUtil;

public class Database extends GenericDatabase<Variable, String> {

	public Database(RelationalModel model) throws Exception {
		super(model);
	}
	
	public String getVariableValue(String varName, boolean closedWorld) throws Exception {
		String lowerCaseName = varName.toLowerCase();
		Variable var = this.entries.get(lowerCaseName);
		// if we have the value, return it
		if(var != null)
			return var.getValue();
		
		// otherwise, get the signature
		int braceIndex = varName.indexOf('(');
		String functionName = varName.substring(0, braceIndex);
		Signature sig = model.getSignature(functionName);		

		// if it's a logically determined predicate, use prolog to retrieve a value
		if(sig.isLogical) {
			if(!sig.isBoolean())
				throw new Exception("Value for logical/evidence variable '" + varName + "' not found in the database and cannot use Prolog to retrieve a value for non-Boolean functions"); // TODO could allow Prolog via a logical coupling
			if(this.isFinalized())
				return BooleanDomain.False;
			else {
				String[] args = varName.substring(braceIndex+1, varName.length()-1).split("\\s*,\\s*");
				return getPrologValue(sig, args, false) ? BooleanDomain.True : BooleanDomain.False;
			}
		}
		
		// if we are making the closed assumption return the default value of
		// false for boolean predicates or raise an exception for non-boolean
		// functions
		if(closedWorld) {
			if(sig.isBoolean())
				return BooleanDomain.False;
			else {
				throw new Exception("Missing database value of " + varName + " - cannot apply closed-world assumption because domain is not boolean: " + sig.returnType);
			}
		}
		
		return null;
	}

	@Override
	protected Variable readEntry(String line) {
		Pattern re_entry = Pattern.compile("(\\w+)\\(([^\\)]+)\\)\\s*=\\s*([^;]*);?");
		Matcher matcher = re_entry.matcher(line);
		if(matcher.matches()) {
			// String key = matcher.group(1) + "(" +
			// matcher.group(2).replaceAll("\\s*", "") + ")";
			Variable var = makeVar(matcher.group(1), matcher.group(2).split("\\s*,\\s*"), matcher.group(3)); // new Variable(matcher.group(1), matcher.group(2).split("\\s*,\\s*"), matcher.group(3), model);
			// System.out.println(var.toString());
			return var;
		}
		return null;
	}
	
	/**
     * 
     * */
	public void readMLNDB(String databaseFilename, boolean ignoreUndefinedNodes) throws Exception {
		boolean verbose = false;

		// read file content
		if(verbose)
			System.out.printf("reading contents of %s...\n", databaseFilename);
		String dbContent = FileUtil.readTextFile(databaseFilename);

		// remove comments
		// if (verbose) System.out.println("  removing comments...");
		Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = comments.matcher(dbContent);
		dbContent = matcher.replaceAll("");

		// read lines
		// if (verbose) System.out.println("  reading items...");
		String arg = "\\w+";
		String argList = "\\s*" + arg + "\\s*(?:,\\s*" + arg + "\\s*)*";
		Pattern re_entry = Pattern.compile("(!?\\w+)\\((" + argList + ")\\)");
		Pattern re_domDecl = Pattern.compile("(\\w+)\\s*=\\s*\\{(" + argList + ")\\}");
		BufferedReader br = new BufferedReader(new StringReader(dbContent));
		String line;
		Variable var;
		while((line = br.readLine()) != null) {
			line = line.trim();
			// parse variable assignment
			matcher = re_entry.matcher(line);
			if(matcher.matches()) {
				if(matcher.group(1).startsWith("!"))
					var = new Variable(matcher.group(1).substring(1), matcher.group(2).trim().split("\\s*,\\s*"), "False", model);
				else
					var = new Variable(matcher.group(1), matcher.group(2).trim().split("\\s*,\\s*"), "True", model);

				addVariable(var, ignoreUndefinedNodes, true);
				// if (++numVars % 100 == 0 && verbose)
				// System.out.println("    " + numVars + " vars read\r");
				continue;
			}

			// parse domain extension
			matcher = re_domDecl.matcher(line);
			if(matcher.matches()) { // parse
				String domNam = matcher.group(1);
				String[] constants = matcher.group(2).trim().split("\\s*,\\s*");
				for(String c : constants)
					fillDomain(domNam, c);
				continue;
			}
			// something else
			if(line.length() != 0)
				System.err.println("Line could not be read: " + line);
		}
	}

	
	/**
	 * @return the values of this database as an array of String[2] arrays,
	 *         where the first element of each is the name of the variable, and
	 *         the second is the value
	 * @throws Exception
	 */
	public String[][] getEntriesAsArray() throws Exception {
		Collection<Variable> vars = getEntries();
		String[][] ret = new String[entries.size()][2];
		int i = 0;
		for(Variable var : vars) {
			ret[i][0] = var.getKeyString();
			ret[i][1] = var.getValue();
			i++;
		}
		return ret;
	}

	@Override
	protected Variable makeVar(String functionName, String[] args, String value) {
		return new Variable(functionName, args, value, model);
	}
	

	/**
	 * 
	 * @param databaseFilename
	 * @throws java.lang.Exception
	 */
	public void readMLNDB(String databaseFilename) throws Exception {
		readMLNDB(databaseFilename, false);
	}

	@Override
	public void fillDomain(String domName, Variable var) throws Exception {
		fillDomain(domName, var.value);		
	}

	@Override
	public String getSingleVariableValue(String varName, boolean closedWorld) throws Exception {
		return getVariableValue(varName, closedWorld);
	}
	
	public void writeMLNDatabase(PrintStream out) throws Exception {
		for(Variable var : this.getEntries()) {
			out.println(var.getPredicate());
		}
	}
}
