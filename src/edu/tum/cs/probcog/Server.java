package edu.tum.cs.probcog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.logic.parser.ParseException;

/**
 * serves a pool of models (base class for specialized server interfaces);
 * uses a dummy command pipe interface
 * @author jain
 */
public class Server {
	ModelPool modelPool; 
	
	public Server(String modelPoolFile) throws IOException, ParseException, Exception {
		modelPool = new ModelPool(modelPoolFile);
	}
	
	protected static Vector<String[]> readListOfLispTuples(String s) {
		Vector<String[]> ret = new Vector<String[]>();		
		s = s.substring(2, s.length()-2); // remove leading and trailing braces
		String[] tuples = s.split("\\)\\s*\\(");
		for(String tuple : tuples)
			ret.add(tuple.split("\\s+"));
		return ret;
	}
	
	/**
	 * @deprecated
	 * @param request
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws Exception
	 */
	public Vector<InferenceResult> query(String request) throws IOException, ParseException, Exception {
		// get request components
		String[] qs = request.split(";");
		String query = qs[0];
		String evidence = qs[1];
		
		// read queries		
		Collection<String[]> queryTuples = readListOfLispTuples(query);
		Vector<String> queries = queriesFromTuples(queryTuples);
		
		// read evidence
		Collection<String[]> evidenceTuples = readListOfLispTuples(evidence);
				
		return query("tableSetting", queries, evidenceTuples);
	}
	
	public Vector<String[]> getPredicates(String modelName) {
		return modelPool.getModel(modelName).getPredicates();
	}
	
	public Vector<String[]> getDomains(String modelName) {
		return modelPool.getModel(modelName).getDomains();
	}
	
	/**
	 * translates a list of LISP-style tuples, such as (sitsAtIn ?PERSON ?SEATING-LOCATION M),
	 * to regular query strings, such as "sitsAtIn(a1,a2,M)"
	 * @param queryTuples
	 * @return collection of query strings
	 */
	protected static Vector<String> queriesFromTuples(Collection<String[]> queryTuples) {		
		Vector<String> queries = new Vector<String>();
		for(String[] tuple : queryTuples) {
			//System.out.println("tuple: (" + StringTool.join("," , tuple) + ")");
			StringBuffer sb = new StringBuffer(tuple[0] + "(");
			for(int i = 1; i < tuple.length; i++) {
				if(i > 1)
					sb.append(',');
				if(tuple[i].charAt(0) == '?')
					sb.append("a" + i);
				else
					sb.append(tuple[i]);
			}
			sb.append(')');
			System.out.println("query: " + sb.toString());
			queries.add(sb.toString());
		}
		return queries;
	}
	
	/**
	 * processes a query
	 * @param modelName the model to use
	 * @param queries a collection of queries, i.e. either predicate/function names, partially grounded predicates/terms (variables in lower-case) or fully grounded predicates/terms
	 * @param evidence a collection of arrays, where each array contains a predicate/function name followed by some arguments, and, in the case of a (non-boolean) function, the value as the last element
	 * NOTE: false boolean evidence is currently unsupported  
	 * @return a vector of inference results with constants already mapped
	 * @throws Exception
	 */
	public Vector<InferenceResult> query(String modelName, Collection<String> queries, Collection<String[]> evidence) throws Exception {
		// get model
		Model model = modelPool.getModel(modelName);		
		// set evidence		
		model.setEvidence(evidence);
		// instantiate model and perform inference
		model.instantiate();
		return model.infer(queries);
	}
	
	/**
	 * 
	 * @param modelName
	 * @param queries
	 * @param evidence a list of evidence atoms, e.g. "foo(bar,baz)"; use an atom even if the variable is actually non-boolean, i.e. use "foo(bar,baz)" for "foo(bar)=baz" 
	 * @return a vector of inference results with constants already mapped
	 * @throws Exception 
	 */
	public Vector<InferenceResult> query(String modelName, Collection<String> queries, Iterable<String> evidence) throws Exception {
		// process the evidence
		Pattern atom = Pattern.compile("(\\w+)\\((.*?)\\)");
		Vector<String[]> newEv = new Vector<String[]>();
		for(String var : evidence) {
			Matcher m = atom.matcher(var);
			if(!m.matches())
				throw new IllegalArgumentException("Evidence variable formatted incorrectly: " + var);
			String fName = m.group(1);
			String[] params = m.group(2).split("\\s*,\\s*");
			String[] seq = new String[params.length+1];
			seq[0] = fName;
			for(int i = 0; i < params.length; i++)
				seq[i+1] = params[i];
			newEv.add(seq);
		}
		// call other query method
		return query(modelName, queries, newEv); 
	}
	
	protected static String inferenceResults2LispTuples(Vector<InferenceResult> results) {
		StringBuffer sb = new StringBuffer('(');
		for(InferenceResult res : results) {
			sb.append('(');
			sb.append(res.functionName).append(' ');
			for(int i = 0; i < res.params.length; i++)
				sb.append(res.params[i]).append(' ');
			sb.append(res.probability);
			sb.append(')');
		}
		sb.append(')');
		return sb.toString();
	}
	
	public static void main(String[] args) {
		try {
			Server server = new Server("/usr/wiss/jain/work/code/SRLDB/models/models.xml");
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			System.err.println("ProbCog Server running...");
			// test cases
			boolean testKnowRob = true;
			boolean testLISP = false;
			if(testKnowRob) {
				// * evidence
				Vector<String> evidence = new Vector<String>();
				evidence.add("takesPartIn(P,M)");
				evidence.add("usesAnyIn(P,Spoon,M)");
				evidence.add("usesAnyIn(P,Bowl,M)");
				evidence.add("usesAnyIn(P,DinnerPlate,M)"); // tests mapping (DinnerPlate is mapped to Plate internally)
				// * queries
				Vector<String> queries = new Vector<String>();
				queries.add("usesAnyIn");
				queries.add("consumesAnyIn");
				// * run inference
				Vector<InferenceResult> results = server.query("tableSetting_fall09", queries, evidence);
				for(InferenceResult res : results) {
					res.print(System.out);
				}
			}			
			if(testLISP) {
				String input = "((sitsAtIn ?PERSON ?SEATING-LOCATION M) (usesAnyIn ?PERSON ?UTENSIL M));((takesPartIn P1 M) (name P1 Anna) (takesPartIn P2 M) (name P2 Bert) (takesPartIn P3 M) (name P3 Dorothy) (mealT M Breakfast))";
				String output = inferenceResults2LispTuples(server.query(input)); 
				System.out.println(output);
			}
			// server loop
			while(true) {
				System.err.println("Waiting for Input");
				String line = br.readLine();
				if(line.equals("close")) {
					System.err.println("ProbCog Server closed");
					break;
				}
				System.err.println("Received query: " + line);
				String result = inferenceResults2LispTuples(server.query(line));
				System.out.println(result);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
