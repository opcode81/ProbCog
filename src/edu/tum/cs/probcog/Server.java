package edu.tum.cs.probcog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Vector;

import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.tools.StringTool;

public class Server {
	ModelPool modelPool; 
	
	public Server(String modelPoolFile) throws IOException, ParseException, Exception {
		modelPool = new ModelPool(modelPoolFile);
	}
	
	public static Vector<String[]> readListOfLispTuples(String s) {
		Vector<String[]> ret = new Vector<String[]>();		
		s = s.substring(2, s.length()-2); // remove leading and trailing braces
		String[] tuples = s.split("\\)\\s*\\(");
		for(String tuple : tuples)
			ret.add(tuple.split("\\s+"));
		return ret;
	}
	
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
	
	/**
	 * translates a list of LISP-style tuples, such as (sitsAtIn ?PERSON ?SEATING-LOCATION M),
	 * to regular query strings, such as "sitsAtIn(a1,a2,M)"
	 * @param queryTuples
	 * @return collection of query strings
	 */
	public static Vector<String> queriesFromTuples(Collection<String[]> queryTuples) {		
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
	
	public Vector<InferenceResult> query(String modelName, Collection<String> queries, Collection<String[]> evidence) throws Exception {
		// get model
		Model model = modelPool.getModel(modelName);		
		// set evidence		
		model.setEvidence(evidence);
		// instantiate model and perform inference
		model.instantiate();
		return model.infer(queries);
	}
	
	public static String inferenceResults2LispTuples(Vector<InferenceResult> results) {
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
			// test case
			boolean test = true;
			if(test) {
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
