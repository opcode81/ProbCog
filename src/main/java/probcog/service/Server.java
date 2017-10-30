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
package probcog.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.exception.ProbCogException;
import probcog.logic.parser.ParseException;


/**
 * Serves a pool of models (base class for specialized server interfaces);
 * the implementation here uses a dummy command pipe interface
 * @author Dominik Jain
 */
public class Server {
	ModelPool modelPool; 
	
	public Server(String modelPoolFile) throws ProbCogException {
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
	 * @throws ProbCogException 
	 */
	public Vector<InferenceResult> query(String request) throws ProbCogException {
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
	
	public Model getModel(String modelName) {
		return modelPool.getModel(modelName);
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
	 * processes a query by setting the evidence, instantiating the model and running the inference procedure
	 * @param modelName the model to use
	 * @param queries a collection of queries, i.e. either predicate/function names, partially grounded predicates/terms (variables in lower-case) or fully grounded predicates/terms
	 * @param evidence a collection of arrays, where each array contains a predicate/function name followed by some arguments and finally the value. For a Boolean function, the value can be omitted (True is default).   
	 * @return a vector of inference results with constants already mapped
	 * @throws ProbCogException 
	 */
	public Vector<InferenceResult> query(String modelName, Collection<String> queries, Collection<String[]> evidence) throws ProbCogException {
		// get model
		Model model = modelPool.getModel(modelName);		
		// set evidence		
		model.setEvidence(evidence);
		// instantiate model and perform inference
		System.out.printf("instantiating model from %s\n", model.toString());
		model.instantiate();
		Vector<InferenceResult> results = model.infer(queries);
		// output evidence and results
		boolean verbose = true;
		if(verbose) {
			System.out.println("\nEvidence:");
			for(String[] e : evidence)
				System.out.println(Arrays.toString(e));
			System.out.println("\nResults:");
			LinkedList<InferenceResult> sortedres = new LinkedList<InferenceResult>(results);
			Collections.sort(sortedres);
			for(InferenceResult r : sortedres)
				r.print(System.out);
		}
		// return results
		return results;
	}
	
	/**
	 * processes a query by setting the evidence, instantiating the model and running the inference procedure 
	 * @param modelName
	 * @param queries
	 * @param evidence a list of evidence atoms, e.g. "foo(bar,baz)"; use an atom even if the variable is actually non-boolean, i.e. use "foo(bar,baz)" for "foo(bar)=baz" 
	 * @return a vector of inference results with constants already mapped
	 * @throws ProbCogException 
	 */
	public Vector<InferenceResult> query(String modelName, Collection<String> queries, Iterable<String> evidence) throws ProbCogException {
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
			System.err.println("ProbCog Server Test");
			// test cases
			boolean testKnowRob = true;
			boolean testLISP = false;
			if(testKnowRob) {
				String modelName = "tableSetting_fall09";
				Model model = server.getModel(modelName);
				// * evidence
				Vector<String> evidence = new Vector<String>();
				evidence.add("takesPartIn(person1,meal1)");
				evidence.add("mealT(meal1,Breakfast)");
				boolean autoUsage = false;
				if(!autoUsage) {
					evidence.add("consumesAnyIn(person1,CowsMilk-Product,meal1)");
					//evidence.add("consumesAnyIn(person1,Cereals,meal1)");
				}
				else {
					String[] observedClasses = new String[]{"Milk", "Cereals"};				
					for(String instance : observedClasses) {
						String objType = instance;
						String constantType = model.getConstantType(objType);
						String predicate = null;
						if(constantType != null) {
							if(constantType.equalsIgnoreCase("domUtensilT"))
								predicate = "usesAnyIn";
							else if(constantType.equalsIgnoreCase("objType_g"))
								predicate = "consumesAnyIn";
							if(predicate != null) { 
								String evidenceAtom = String.format("%s(P,%s,M)", predicate, objType);  
								evidence.add(evidenceAtom);
							}
							else
								System.err.println("Warning: Evidence on instance '" + instance + "' not considered because it is neither a utensil nor a consumable object known to the model.");
						}
						else
							System.err.println("Warning: Evidence on instance '" + instance + "' not considered because its type is not known to the model.");
					}
				}
				// * queries
				Vector<String> queries = new Vector<String>();
				queries.add("usesAnyIn");
				queries.add("consumesAnyIn");
				// * run inference
				Vector<InferenceResult> results = server.query(modelName, queries, evidence);
				System.out.println();
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
