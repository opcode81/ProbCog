package edu.tum.cs.srldb.prolog; 
// TODO this package should be moved/renamed to edu.tum.cs.probcog.prolog, as the srldb package is concerned strictly with data collection

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
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
 * @author Daniel Nyga
 * 
 */
public class PrologInterface {

	public static final String UNKNOWN_TYPE = "TYPE_UNKNOWN";

	/**
	 * Maps object instances (in particular the objects located on the table) to
	 * their concepts
	 */
	private static Map<String, String> objectTypes = new HashMap<String, String>();

	private static String modelPool = "/data/srldb/models/models.xml";

	private static String modelName = "tableSetting_fall09";
	
	private static Server server = null;

	/**
	 * Initialize the Prolog engine.
	 */
//	static {
//		try {
//			
//			JPL.init(new String[] {"pl"});
//	//		Vector<String> args = new Vector<String>(Arrays.asList(Prolog.get_default_init_args()));
//	//		args.add("-G128M");
////			args.add("-q");
//	//		args.add("-nosignals");
//	//		Prolog.set_default_init_args(args.toArray(new String[0]));
//
//			// load the appropriate startup file for this context
//		//	new Query("ensure_loaded('/home/tenorth/work/owl/gram_tabletop.pl')").oneSolution();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
	

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

//		System.err.println("Executing query: " + query);

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
	 *  <code>objectTypes</code>.
	 */
	public static void setObjectsOnTable(String[] objs) {
		
		for(String identifier : objs)
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
//				System.out.println(instance + " (" + localClassName + ")");
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

		Map<String, Vector<Object>> answer = executeQuery(
				"rdf_has('" + instanceName + "', rdf:type, Type)", "");

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

		Map<String, Vector<Object>> answer = executeQuery(
				"rdf_has('" + instanceName + "', rdf:type, Type)", "");

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
		//	evidence.add("mealT(M,Breakfast)");

			// add evidence on utensils and consumed goods
			for(String instance : objectTypes.keySet()) {
				String objType = objectTypes.get(instance);
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
	
	public static String[][] performInference(String modelName, String[] query, String[]... evidence) {
		try {
			// Retrieve all objects that are already on the table
			queryObjectTypes();

			Server srldbServer = new Server(modelPool);
			Model model = srldbServer.getModel(modelName);

			// Generate evidence for all objects already found on the table
			Vector<String> evidences = new Vector<String>();
			
			// add evidence on utensils and consumed goods
			for(String[] e : evidence) {
					
				StringBuilder evString = new StringBuilder();
				
				evString.append(e[0]);
				evString.append("(");
				for (int i = 1; i < e.length; i++) {
				
					String arg = inferObjectClass(e[i]);
					if (arg == null)
						arg = e[i];
					evString.append(arg + (i < e.length - 1 ? "," : ""));
				}
				evString.append(")");
				evidences.add(evString.toString());
			//	System.out.println(evString);
			}

			// Generate queries: "usesAnyIn" for utensils, "consumesAnyIn" for
			// edible stuff
			Vector<String> queries = new Vector<String>();
			for (String q : query) 
				queries.add(q);
				

			// Run the inference process
			Vector<InferenceResult> results = srldbServer.query(modelName,
					queries, evidences);

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
	
	public static String[] getArgsForPredicate(String predicate, String modelName) {
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

	//	getMissingObjectsOnTable();

		try {
			Server s = new Server(modelPool);
			
			for (String[] p : s.getPredicates(modelName)) {
				for (String d : p)
					System.out.print(d + ";");
				System.out.println();
			}
			
			System.out.println();
			
			for (String[] p : s.getDomains(modelName)) {
				for (String d : p)
					System.out.print(d + ";");
				System.out.println();
			}
			
			performInference(modelName, new String[] { "usesAnyForIn", "consumesAnyIn" }, new String[] {"takesPartIn", "P", "DinnerPlate"}, new String[] {"mealT", "M", "Breakfast"});
			
			
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
	}
}
