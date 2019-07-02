/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
package probcog;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

import probcog.exception.ProbCogException;
import probcog.logic.sat.weighted.WeightedFormula;
import probcog.srl.Database;
import probcog.srl.mln.MarkovLogicNetwork;
import probcog.srl.mln.MarkovRandomField;
import probcog.srl.mln.inference.InferenceAlgorithm;
import probcog.srl.mln.inference.InferenceResult;
import probcog.srl.mln.inference.MPEInferenceAlgorithm;
import probcog.srl.mln.inference.MCSAT;
import probcog.srl.mln.inference.MaxWalkSAT;
import probcog.srl.mln.inference.Toulbar2Inference;
import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.StringTool;

/**
 * MLN command-line inference tool 
 * @author Dominik Jain
 */
public class MLNinfer {

	enum Algorithm {MaxWalkSAT, MCSAT, Toulbar2, MaxWalkSATRooms};
	
	public static void main(String[] args) throws FileNotFoundException, ProbCogException {
		String[] mlnFiles = null;
		String dbFile = null;
		String query = null;
		Integer maxSteps = null;
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
					throw new ProbCogException("Argument '" + args[i] + "' for algorithm-specific parameterization is incorrectly formatted.");
				params.put(pair[0], pair[1]);
			}
			else
				System.err.println("Warning: unknown option " + args[i] + " ignored!");
		}			
		if(mlnFiles == null || query == null) {
			System.out.println("\n usage: MLNinfer <-i <(comma-sep.) MLN file(s)>> [-e <evidence db file>] <-q <comma-sep. queries>> [options]\n\n"+
								 "    -maxSteps #      the maximum number of steps to take, where applicable (default determined by algorithm, if any)\n" +
								 "    -r <filename>    save results to file\n" + 
								 "    -mws             algorithm: MaxWalkSAT (MAP inference)\n" +
								 "    -mcsat           algorithm: MC-SAT (default)\n" +
								 "    -t2              algorithm: Toulbar2 branch & bound\n" +									 
						         "    -debug           debug mode with additional outputs\n" +
						         "    -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n" +
						         "    --<key>=<value>  set algorithm-specific parameter\n" 
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
		System.out.printf("reading model %s...\n", StringTool.join(", ", mlnFiles));
		MarkovLogicNetwork mln = new MarkovLogicNetwork(mlnFiles);
		
		// instantiate ground model
		Database db = new Database(mln);
		if (dbFile == null) {
			System.out.printf("evidence database is empty\n", dbFile);
		}
		else {
			System.out.printf("reading database %s...\n", dbFile);
			db.readMLNDB(dbFile);
		}
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
		System.out.println("starting inference process...");
		Stopwatch sw = new Stopwatch();
		sw.start();
		InferenceAlgorithm infer = null;
		switch(algo) {
		case MCSAT:
			infer = new MCSAT(mrf);
			break;
		case MaxWalkSAT:
			infer = new MaxWalkSAT(mrf); 
			break;
		case Toulbar2:
			infer = new Toulbar2Inference(mrf);
			break;
		default:
			throw new RuntimeException("Unhandled algorithm: " + algo);
		}			
		infer.setDebugMode(debug);
		if(maxSteps != null) {
			if (!infer.getParameterHandler().isSupportedParameter("maxSteps"))
				System.out.println("Note: Parameter 'maxSteps' not handled by " + algo + ", ignored."); 
			else
				infer.setParameterByName("maxSteps", maxSteps);	
		}
		infer.getParameterHandler().handle(params, true);
		System.out.printf("algorithm: %s\n", infer.getAlgorithmName());
		List<InferenceResult> results = infer.infer(queries);
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
        if(infer instanceof MPEInferenceAlgorithm) {
        	MPEInferenceAlgorithm mapi = (MPEInferenceAlgorithm)infer;
        	double value = mrf.getWorldValue(mapi.getSolution());
        	System.out.printf("\nsolution value: %f\n", value);
        	System.out.printf("\nsum of unsatisfied formula weights: %f\n", mrf.getSumOfUnsatClauseWeights(mapi.getSolution()));
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
