import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.logic.sat.weighted.MaxWalkSATRoom;
import edu.tum.cs.logic.sat.weighted.WeightedFormula;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.srl.mln.inference.InferenceAlgorithm;
import edu.tum.cs.srl.mln.inference.InferenceResult;
import edu.tum.cs.srl.mln.inference.MAPInferenceAlgorithm;
import edu.tum.cs.srl.mln.inference.MCSAT;
import edu.tum.cs.srl.mln.inference.MaxWalkSAT;
import edu.tum.cs.srl.mln.inference.Toulbar2MAPInference;
import edu.tum.cs.util.Stopwatch;

/**
 * MLN command-line inference tool 
 * @author jain
 */
public class MLNinfer {

	enum Algorithm {MaxWalkSAT, MCSAT, Toulbar2, MaxWalkSATRooms};
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String[] mlnFiles = null;
			String dbFile = null;
			String query = null;
			int maxSteps = 1000;
			String resultsFile = null;
			Algorithm algo = Algorithm.MCSAT;
			String[] cwPreds = null;
			boolean debug = false;
			HashMap<String,Object> params = new HashMap<String,Object>();
			
			// read arguments
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-i"))
					mlnFiles = args[++i].split(",");
				else if(args[i].equals("-q"))
					query = args[++i];
				else if(args[i].equals("-e"))
					dbFile = args[++i];				
				else if(args[i].equals("-r"))
					resultsFile = args[++i];				
				else if(args[i].equals("-cw"))
					cwPreds = args[++i].split(",");		
				else if(args[i].equals("-maxSteps"))
					maxSteps = Integer.parseInt(args[++i]);
				else if(args[i].equals("-mws"))
					algo = Algorithm.MaxWalkSAT;
				else if(args[i].equals("-mwsr"))
					algo = Algorithm.MaxWalkSATRooms;
				else if(args[i].equals("-mcsat"))
					algo = Algorithm.MCSAT;
				else if(args[i].equals("-t2"))
					algo = Algorithm.Toulbar2;
				else if(args[i].equals("-debug"))
					debug = true;
				else if(args[i].startsWith("-p") || args[i].startsWith("--")) { // algorithm-specific parameter
					String[] pair = args[i].substring(2).split("=");
					if(pair.length != 2)
						throw new Exception("Argument '" + args[i] + "' for algorithm-specific parameterization is incorrectly formatted.");
					params.put(pair[0], pair[1]);
				}
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(mlnFiles == null || dbFile == null || query == null) {
				System.out.println("\n usage: MLNinfer <-i <(comma-sep.) MLN file(s)>> <-e <evidence db file>> <-q <comma-sep. queries>> [options]\n\n"+
									 "    -maxSteps #      the maximum number of steps to take [default: 1000]\n" +
									 "    -r <filename>    save results to file\n" + 
									 "    -mws             algorithm: MaxWalkSAT (MAP inference)\n" +
									 "    -mcsat           algorithm: MC-SAT (default)\n" +
									 "    -t2              algorithm: Toulbar2 branch & bound\n" +									 
							         "    -debug           debug mode with additional outputs\n" +
							         "    -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n"
									 );
				return;
			}			

			// determine queries
			Pattern comma = Pattern.compile("\\s*,\\s*");
			String[] candQueries = comma.split(query);
			Vector<String> queries = new Vector<String>();
			String q = "";
			for(int i = 0; i < candQueries.length; i++) {
				if(!q.equals(""))
					q += ",";
				q += candQueries[i];
				if(balancedParentheses(q)) {
					queries.add(q);
					q = "";
				}
			}
			if(!q.equals(""))
				throw new IllegalArgumentException("Unbalanced parentheses in queries");

			// load relational model
			Stopwatch constructSW = new Stopwatch();
			constructSW.start();
			System.out.printf("reading model %s...\n", mlnFiles.toString());
			MarkovLogicNetwork mln = new MarkovLogicNetwork(mlnFiles);
			
			// instantiate ground model
			System.out.printf("reading database %s...\n", dbFile);
			Database db = new Database(mln);
			db.readMLNDB(dbFile);
			if(cwPreds != null) {
				for(String predName : cwPreds)
					db.setClosedWorldPred(predName);
			}
			
			System.out.printf("creating ground MRF...\n");
			MarkovRandomField mrf = mln.ground(db);
			if(debug) {
				System.out.println("MRF:");
				for(WeightedFormula wf : mrf)
					System.out.println("  " + wf.toString());
			}
			constructSW.stop();
			
			// run inference
			Stopwatch sw = new Stopwatch();
			sw.start();
			InferenceAlgorithm infer = null;
			switch(algo) {
			case MCSAT:
				infer = new MCSAT(mrf);
				break;
			case MaxWalkSAT:
			case MaxWalkSATRooms:
				infer = new MaxWalkSAT(mrf, algo == Algorithm.MaxWalkSAT ? edu.tum.cs.logic.sat.weighted.MaxWalkSAT.class : MaxWalkSATRoom.class); 
				break;
			case Toulbar2:
				infer = new Toulbar2MAPInference(mrf);
				break;
			}			
			infer.setDebugMode(debug);
			infer.getParameterHandler().handle(params, true);
			System.out.printf("algorithm: %s, steps: %d\n", infer.getAlgorithmName(), maxSteps);
			List<InferenceResult> results = infer.infer(queries, maxSteps);
	        sw.stop();
	        
	        // show results
	        System.out.printf("\nconstruction time: %.4fs, inference time: %.4fs\n", constructSW.getElapsedTimeSecs(), sw.getElapsedTimeSecs());
	        System.out.println("results:");
	        Collections.sort(results);
	        PrintStream out = null;
	        if(resultsFile != null)
	        	out = new PrintStream(new File(resultsFile));
	        for(InferenceResult r : results) {
	        	r.print();
	        	if(out != null)
	        		out.printf("%s %f\n", r.ga.toString().replace(" ", ""), r.value);
	        }
	        if(out != null) out.close();
	        if(infer instanceof MAPInferenceAlgorithm) {
	        	MAPInferenceAlgorithm mapi = (MAPInferenceAlgorithm)infer;
	        	double value = mrf.getWorldValue(mapi.getSolution());
	        	System.out.printf("\nsolution value: %f\n", value);
	        }
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean balancedParentheses(String s) {
		int n = 0;
		for(int i = 0; i < s.length(); i++) {
			if(s.charAt(i) == '(')
				n++;
			else if(s.charAt(i) == ')')
				n--;
		}
		return n == 0;
	}
}
