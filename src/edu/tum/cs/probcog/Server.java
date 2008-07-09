package edu.tum.cs.probcog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Vector;

import edu.tum.cs.bayesnets.relational.core.ABL;
import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.logic.parser.ParseException;

public class Server {
	public Server() {
		
	}
	
	public static Collection<String[]> readListOfLispTuples(String s) {
		Vector<String[]> ret = new Vector<String[]>();		
		s = s.substring(2, s.length()-2); // remove leading and trailing braces
		String[] tuples = s.split("\\)\\s*\\(");
		for(String tuple : tuples)
			ret.add(tuple.split("\\s+"));
		return ret;
	}
	
	public String query(String request) throws IOException, ParseException, Exception {
			
		// get request components
		String[] qs = request.split(";");
		String query = qs[0];
		String evidence = qs[1];
		
		// read model
		Model model = new BLNModel("/usr/stud/waldhers/kitchen/new/meals_any_for.blog", "/usr/stud/waldhers/kitchen/new/meals_any_for.learnt.xml", "/usr/stud/waldhers/kitchen/new/meals_any_for.bln");
		
		// read queries
		Collection<String> queries = new Vector<String>();
		Collection<String[]> queryTuples = readListOfLispTuples(query);
		for(String[] tuple : queryTuples) {
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
		
		// read and set evidence
		Collection<String[]> evidenceTuples = readListOfLispTuples(evidence);
		model.setEvidence(evidenceTuples);
		
		// instantiate model and perform inference
		model.instantiate();
		Vector<InferenceResult> results = model.infer(queries);
		
		// create list of list tuples for results
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
			Server server = new Server();
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			System.err.println("ProbCog Server running...");
			// test case
			boolean test = true;
			if(test) {
				String input = "((sitsAtIn ?PERSON ?SEATING-LOCATION M) (usesAnyIn ?PERSON ?UTENSIL M));((takesPartIn P1 M) (name P1 Anna) (takesPartIn P2 M) (name P2 Bert) (takesPartIn P3 M) (name P3 Dorothy) (mealT M Breakfast))";
				String output = server.query(input); 
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
				server.query(line);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
