package edu.tum.cs.srldb.prolog;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import jpl.Query;
import jpl.fli.Prolog;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.probcog.InferenceResult;
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

	private static String modelPool = "C:/srldb/models/models.xml";

	private static String modelName = "tableSetting_fall09";

	/**
	 * Initialize the Prolog engine.
	 */
	static {
		try {
			Vector<String> args = new Vector<String>(Arrays.asList(Prolog
					.get_default_init_args()));
			args.add("-G128M");
			args.add("-q");
			args.add("-nosignals");
			args.add("C:/owl/gram_ias.pl");
			Prolog.set_default_init_args(args.toArray(new String[0]));

		} catch (Exception e) {
			e.printStackTrace();
		}
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

		System.err.println("Executing query: " + query);

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
	 * Retrieves all objects that are located on the kitchen table from the
	 * prolog system and stores them in <code>objectTypes</code>.
	 */
	public static void queryObjectsOnTable() {

		Map<String, Vector<Object>> answer = executeQuery(
				"rdf_triple(kitchen:'on-Physical', Top, Bottom), rdf_triple(rdf:type, Bottom, kitchen:'KitchenTable')",
				"");
		for (Iterator<String> i = answer.keySet().iterator(); i.hasNext();) {
			String key = i.next();

			// Only the objects _on_ the table are interesting
			if (key.equals("Top")) {
				for (Iterator<Object> i2 = answer.get(key).iterator(); i2
						.hasNext();) {
					String instanceName = ((String) i2.next()).replaceAll("'",
							"");

					if (objectTypes.get(instanceName) == null) {
						System.out.println("found object on table: "
								+ instanceName);
						objectTypes.put(instanceName, UNKNOWN_TYPE);
					}
				}
			}
		}
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
				System.out.println(instance + " (" + localClassName + ")");
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
				"rdf_triple(rdf:type, '" + instanceName + "', Type)", "");

		for (Iterator<String> i = answer.keySet().iterator(); i.hasNext();) {
			String key = i.next();
			if (key.equals("Type") && answer.get(key).size() > 0)
				return ((String) answer.get(key).get(0)).replaceAll("'", "");
		}

		throw new RuntimeException("ERROR: Cannot infer type of "
				+ instanceName);
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
			queryObjectsOnTable();
			queryObjectTypes();

			Server srldbServer = new Server(modelPool);

			// Generate evidences for all object already found on the table
			Vector<String> evidence = new Vector<String>();
			evidence.add("takesPartIn(P,M)");

			// Maybe we can figure out the type of the meal
			// evidence.add("mealT(M,Breakfast)");

			for (String instance : objectTypes.keySet()) {
				evidence.add(String.format("usesAnyIn(P,%s,M)", objectTypes
						.get(instance)));
				System.out.println(String.format("usesAnyIn(P,%s,M)",
						objectTypes.get(instance)));
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

	public static void main(String[] args) {

		setPerception("C:/srldb/models/kitchen/tableSetting/ias_entities.owl");

		getMissingObjectsOnTable();

	}
}
