package edu.tum.cs.srldb.prolog;

// TODO this package should be moved/renamed to edu.tum.cs.probcog.prolog, as the srldb package is concerned strictly with data collection

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import jpl.JPL;
import jpl.Query;
import jpl.fli.Prolog;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.probcog.InferenceResult;
import edu.tum.cs.probcog.Model;
import edu.tum.cs.probcog.Server;

/**
 * Interface to the Prolog system for figuring out which objects are missing on
 * the table.
 * 
 * @author Daniel Nyga, Moritz Tenorth
 * 
 */
public class PrologInterface {

	public static final String UNKNOWN_TYPE = "TYPE_UNKNOWN";

	/**
	 * Maps object instances (in particular the objects located on the table) to
	 * their concepts
	 */
	private static Map<String, String> objectTypes = new HashMap<String, String>();

	private static String modelPool = "/home/tenorth/work/srldb/models/models.xml";

	private static String modelName = "tableSetting_fall09";

	private static Server server = null;

	/**
	 * Reset the Prolog interface
	 */
	public static void reset() {
		objectTypes.clear();
		server = null;
	}

	/**
	 * Execute the given query. Assert the given premises and retract them after
	 * querying.
	 * 
	 * @param query
	 *            the query.
	 * @return the HashMap representing the bindings of the variables.
	 */
	public static Map<String, Vector<Object>> executeQuery(String query,
			String plFile) {

		// System.err.println("Executing query: " + query);

		HashMap<String, Vector<Object>> result = new HashMap<String, Vector<Object>>();
		Hashtable[] solutions;

		Query q = new Query("expand_goal((" + query + "),_9), call(_9)");

		// Due to bugs we have to check for one answer beforehand.
		if (!q.hasMoreSolutions())
			return new HashMap<String, Vector<Object>>();
		Hashtable oneSolution = q.nextSolution();
		if (oneSolution.isEmpty()) // Due to a bug consulting a file without
			// anything else results in shutdown
			return new HashMap<String, Vector<Object>>(); // I will try to
		// prevent it with
		// this construction

		// Restart the query and fetch everything.
		q.rewind();
		solutions = q.allSolutions();

		for (Object key : solutions[0].keySet()) {
			result.put(key.toString(), new Vector<Object>());
		}

		// Build the result
		for (int i = 0; i < solutions.length; i++) {
			Hashtable solution = solutions[i];
			for (Object key : solution.keySet()) {
				String keyStr = key.toString();

				if (!result.containsKey(keyStr)) {

					// previously unknown column, add result vector
					Vector<Object> resultVector = new Vector<Object>();
					resultVector.add(i, solution.get(key).toString());
					result.put(keyStr, resultVector);

				}
				// Put the solution into the correct vector
				Vector<Object> resultVector = result.get(keyStr);
				resultVector.add(i, solution.get(key).toString());
			}
		}
		// Generate the final QueryResult and return
		return result;
	}

	/**
	 * Put all objects that are located on the kitchen table into the
	 * <code>objectTypes</code>.
	 */
	public static void setObjectsOnTable(String[] objs) {

		for (String identifier : objs)
			objectTypes.put(identifier, UNKNOWN_TYPE);
	}

	/**
	 * Retrieves the concepts for all object instances that are stored in
	 * <code>objectTypes</code>.
	 */
	public static void queryObjectTypes() {

		for (Iterator<String> i = objectTypes.keySet().iterator(); i.hasNext();) {
			String instance = i.next();
			String type = inferObjectType(instance);
			if (objectTypes.get(instance).equals(UNKNOWN_TYPE)) {
				String localClassName = getLocalClassName(type);
				objectTypes.put(instance, localClassName);
			}
		}

	}

	/**
	 * Retrieves the local class name of a class specified by the given URI,
	 * e.g.
	 * <code>uri="http://www9.in.tum.de/~tenorth/ontology/kitchen.owl#Cup"</code>
	 * will return <code>"Cup"</code>.
	 * 
	 * @param url
	 * @return
	 */
	public static String getLocalClassName(String uri) {

		Map<String, Vector<Object>> answer = executeQuery(
				"rdf_split_url(Base, Local, '" + uri + "')", "");

		for (Iterator<String> i = answer.keySet().iterator(); i.hasNext();) {
			String key = i.next();
			if (key.equals("Local") && answer.get(key).size() > 0)
				return ((String) answer.get(key).get(0)).replaceAll("'", "");
		}

		throw new RuntimeException(
				"ERROR: Could not determine local class name.");
	}

	/**
	 * Retrieves the URI of the class of the object given by the instance
	 * <code>instanceName</code>.
	 * 
	 * @param instanceName
	 * @return
	 */
	public static String inferObjectType(String instanceName) {

		Map<String, Vector<Object>> answer = executeQuery("rdf_has('"
				+ instanceName + "', rdf:type, Type)", "");

		for (Iterator<String> i = answer.keySet().iterator(); i.hasNext();) {
			String key = i.next();
			if (key.equals("Type") && answer.get(key).size() > 0)
				return ((String) answer.get(key).get(0)).replaceAll("'", "");
		}

		throw new RuntimeException("ERROR: Cannot infer type of "
				+ instanceName);
	}

	/**
	 * Retrieves the URI of the class of the object given by the instance
	 * <code>instanceName</code>.
	 * 
	 * @param instanceName
	 * @return
	 */
	public static String inferObjectClass(String instanceName) {

		Map<String, Vector<Object>> answer = executeQuery("rdf_has('"
				+ instanceName + "', rdf:type, Type)", "");

		for (Iterator<String> i = answer.keySet().iterator(); i.hasNext();) {
			String key = i.next();
			if (key.equals("Type") && answer.get(key).size() > 0)
				return ((String) answer.get(key).get(0)).replaceAll("'", "");
		}

		return null;
	}

	public static void setPerception(String owlFile) {
		executeQuery("owl_parser:owl_parse('" + owlFile
				+ "', false, false, true)", "");
	}

	public static void setModelName(String modelName) {
		PrologInterface.modelName = modelName;
	}

	public static void setModelPool(String modelPool) {
		PrologInterface.modelPool = modelPool;
	}

	public static String[][] getMissingObjectsOnTable() {

		try {
			// Retrieve all objects that are already on the table
			queryObjectTypes();

			Server srldbServer = new Server(modelPool);
			Model model = srldbServer.getModel(modelName);

			// Generate evidence for all objects already found on the table
			Vector<String> evidence = new Vector<String>();
			evidence.add("takesPartIn(P,M)");

			// Maybe we can figure out the type of the meal
			// evidence.add("mealT(M,Breakfast)");

			// add evidence on utensils and consumed goods
			for (String instance : objectTypes.keySet()) {
				String objType = objectTypes.get(instance);
				String constantType = model.getConstantType(objType);
				String predicate = null;
				if (constantType != null) {
					if (constantType.equalsIgnoreCase("domUtensilT"))
						predicate = "usesAnyIn";
					else if (constantType.equalsIgnoreCase("objType_g"))
						predicate = "consumesAnyIn";
					if (predicate != null) {
						String evidenceAtom = String.format("%s(P,%s,M)",
								predicate, objType);
						evidence.add(evidenceAtom);
					} else
						System.err
								.println("Warning: Evidence on instance '"
										+ instance
										+ "' not considered because it is neither a utensil nor a consumable object known to the model.");
				} else
					System.err
							.println("Warning: Evidence on instance '"
									+ instance
									+ "' not considered because its type is not known to the model.");
			}

			// Generate queries: "usesAnyIn" for utensils, "consumesAnyIn" for
			// edible stuff
			Vector<String> queries = new Vector<String>();
			queries.add("usesAnyIn");
			queries.add("consumesAnyIn");

			// Run the inference process
			Vector<InferenceResult> results = srldbServer.query(modelName,
					queries, evidence);

			String[][] result = new String[results.size()][2];
			int i = 0;
			for (InferenceResult res : results) {
				result[i][0] = res.params[1];
				result[i][1] = res.probability + "";
				System.out.println("object: " + result[i][0] + "; prob="
						+ result[i][1]);
				i++;
			}

			return result;

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Reads the evidence for a particular predicate and returns all 
	 * evidence predicates resulting from this query.
	 * 
	 * @param modelName
	 * @param predicate
	 * @return
	 */
	public static String[] evidenceForPredciate(Model model, String moduleName, String predicate) {

		Set<String> result = new HashSet<String>();
		String[] args = getArgsForPredicate(predicate, model.getName());

		StringBuilder query = new StringBuilder();
		query.append(String.format("%s:%s(", moduleName, predicate));

		for (int i = 0; i < args.length; i++) {
			query.append(String.format("Arg%d", i));
			if (i < args.length - 1)
				query.append(", ");
		}
		query.append(")");

		System.out.println("Checking evidence for: "
				+ query.toString());

		Map<String, Vector<Object>> answer = executeQuery(query.toString(), "");

		if (answer.get("Arg0") == null) // no solution found
			return new String[0];

		String[][] resultArray = new String[answer.get("Arg0").size()][args.length];

		for (String arg : answer.keySet()) {
			int argIndex = Integer.valueOf(arg.substring(3));

			Vector<Object> values = answer.get(arg);
			for (int i = 0; i < values.size(); i++) {
				resultArray[i][argIndex] = ((String) values.get(i)).replace(
						"'", "");
			}
		}

		for (int sol = 0; sol < resultArray.length; sol++) {

			StringBuilder evidence = new StringBuilder();
			evidence.append(String.format("%s(", predicate));

			boolean discard = false;
			for (int arg = 0; arg < resultArray[sol].length; arg++) {
				String value = resultArray[sol][arg];

				String clazzURI = inferObjectClass(value);
				if (clazzURI != null) {
					value = getLocalClassName(clazzURI);

					// to exclude type assertions such as 'Thing' or the like,
					// only use known constants
					if (model.constantMapToProbCog.get(value) == null) {
						discard = true;
						break;
					}
				}
				evidence.append(value);
				if (arg < args.length - 1)
					evidence.append(",");
			}
			evidence.append(")");
			if (!discard) {
				result.add(evidence.toString());
				System.out.println("  -> found evidence: "
						+ evidence.toString());
			}
		}

		return result.toArray(new String[0]);
	}

	public static String[][][] performInference(String modelName, String moduleName, String[] query) {
		try {

			reset();

			Server srldbServer = new Server(modelPool);
			Model model = srldbServer.getModel(modelName);

			// Generate evidence for all objects already found on the table
			Vector<String> evidence = new Vector<String>();

			// Get evidence
			String[] predicates = getPredicatesForModel(modelName);

			for (String pred : predicates) {
				String[] ev = evidenceForPredciate(model, moduleName, pred);

				for (String e : ev)
					evidence.add(e);
			}

			
			// vector of queries to be sent
			Vector<String> queries = new Vector<String>();
			
			// mapping from predicate name to the indices of query parameters
			HashMap<String, Vector<Integer>> queriesPredsParams = new HashMap<String, Vector<Integer>>();
			
			
			for (String q : query) {
				
				// split into predicate name and parameters, remember query parameters
				// in order to later determine query parameters
				
				if(q.contains("(")&&q.contains("")) {
					String pred = q.split("\\(")[0];
					String[] params = q.split("\\(")[1].split("\\)")[0].split(",");
					
					Vector<Integer> qParams = new Vector<Integer>();
					for(int k=0;k<params.length;k++) {
	
						if(params[k].contains("?"))
							qParams.add(k);
					}
					queriesPredsParams.put(pred, qParams);
					queries.add(pred);
					
				} else {
					queriesPredsParams.put(q, new Vector<Integer>());
					queries.add(q);
				}
				
				
			}
			
			// Run the inference process
			Vector<InferenceResult>  inferenceresults = srldbServer.query(modelName, queries, evidence);
			Vector<Vector<String[]>> resultvector     = new Vector<Vector<String[]>>();

			String lastQuery = "";
			Vector<String[]> r = null;

			
			for (InferenceResult ires : inferenceresults) {
				
				// start new result vector
				if(!ires.functionName.equals(lastQuery)) {
					
					if(r!=null) {
						resultvector.add(r);
					}
					r = new Vector<String[]>();
					lastQuery=ires.functionName;
				}
				
				if(queriesPredsParams.get(ires.functionName).size()==0) {
				
					// there are no free variables -> return possible predicate/params like usesAnyIn(P1, Cup, Meal2)
					r.add(new String[]{ires.toString().split("  ")[1], ires.toString().split("  ")[0]});

										
				} else {
					
					// only return the query variables [[param1-param2-param3, 0.2345],...]
					String params = "";
					for(int k : queriesPredsParams.get(ires.functionName)) {
						
						params += ires.params[k];
						if(k<queriesPredsParams.get(ires.functionName).size()) {
							params+= "_";
						}
					}
					r.add(new String[]{params, ""+ires.probability});
				} 
			}
			resultvector.add(r);

			
			String[][][] resultarray = new String[resultvector.size()][][];
			for(int i=0;i<resultvector.size();i++) {
				
				resultarray[i] = new String[resultvector.get(i).size()][];
			
				for(int j=0;j<resultvector.get(i).size();j++) {
					resultarray[i][j] = resultvector.get(i).get(j);
				}
			}
			
			return resultarray;

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private static Server getServer() {
		if (server == null) {
			try {
				server = new Server(modelPool);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage());
			}
		}

		return server;
	}

	public static String[] getPredicatesForModel(String modelName) {
		Server s = getServer();

		Vector<String[]> predicates = s.getPredicates(modelName);

		String[] result = new String[predicates.size()];
		for (int i = 0; i < result.length; i++)
			result[i] = predicates.get(i)[0];

		return result;
	}

	public static String[] getArgsForPredicate(String predicate,
			String modelName) {
		Server s = getServer();

		String[] predicates = getPredicatesForModel(modelName);
		Vector<String[]> args = s.getPredicates(modelName);

		for (int i = 0; i < predicates.length; i++) {

			if (predicates[i].equals(predicate) && args.elementAt(i).length > 1) {
				String[] arguments = new String[args.elementAt(i).length - 1];

				for (int j = 0; j < arguments.length; j++)
					arguments[j] = args.elementAt(i)[j + 1];

				return arguments;
			}

		}

		return new String[0];
	}

	public static void main(String[] args) {
		performInference("tableSetting_fall09", "mod_probcog_tablesetting", new String[]{"usesAnyIn(person1, ?, meal1)", "sitsAtIn(person1, ?, bla)"});
	}
}
