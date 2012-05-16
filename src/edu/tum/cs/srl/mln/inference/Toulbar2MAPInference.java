/*
 * Created on Aug 19, 2009
 */
package edu.tum.cs.srl.mln.inference;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.wcsp.WCSP;
import edu.tum.cs.wcsp.WCSPConverter;

/**
 * Toulbar2 branch & bound inference wrapper.
 * Requires installation of the toulbar2 executable in the system PATH.
 * 
 * @author jain
 */
public class Toulbar2MAPInference extends MAPInferenceAlgorithm {
	
	protected PossibleWorld state;
	protected WCSPConverter converter = null;
	protected String wcspFilename = "temp.wcsp";
	protected String toulbar2Args = "";

	public Toulbar2MAPInference(MarkovRandomField mrf) throws Exception {
		super(mrf);
		state = new PossibleWorld(mrf.getWorldVariables());
		this.paramHandler.add("toulbar2Args", "setToulbar2Args");
	}
	
	/**
	 * sets toulbar2 command-line arguments
	 * @param args
	 */
	public void setToulbar2Args(String args) {
		toulbar2Args = args;
	}

	@Override
	public double getResult(GroundAtom ga) {
		return state.get(ga.index) ? 1.0 : 0.0;
	}

	public WCSPConverter constructWCSP(String filename, boolean cache) throws Exception {
		if(converter != null)
			throw new Exception("WCSP was already constructed");
		// perform conversion to WCSP
		this.wcspFilename = filename;
		if(verbose) System.out.println("performing WCSP conversion...");
		converter = new WCSPConverter(mrf);
		paramHandler.addSubhandler(converter);
		converter.setCacheConstraints(cache);
		WCSP wcsp = converter.run();
		wcsp.writeWCSP(new PrintStream(wcspFilename), "WCSPFromMLN");
		return converter;
	}
	
	@Override
	public ArrayList<InferenceResult> infer(Iterable<String> queries) throws Exception {
		
		// construct WCSP if necessary
		if(converter == null)
			constructWCSP(this.wcspFilename, false);
		
		// run Toulbar2
		String command = "toulbar2 " + wcspFilename + " -s "  + toulbar2Args;
		if(verbose) System.out.println("running WCSP solver: " + command);
		Process p = Runtime.getRuntime().exec(command);
		InputStream s = p.getInputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(s));
		String solution = null;
		while(true) {
			try {
				String l = br.readLine();
				if(l == null)
					break;
				if(debug)
					System.out.println(l);
				if(l.startsWith("New solution:")) {
					solution = br.readLine();
					if(debug)
						System.out.println(solution);
				}
			}
			catch(IOException e) {
				break;
			}			
		}	
		
		if(solution == null)
			throw new Exception("No solution was found");
		
		// set evidence (as in the WCSP, evidence variables are removed)
		state.setEvidence(mrf.getDb());
		
		// set solution state
		if(verbose) System.out.println("WCSP solution: " + solution);
		String[] solutionParts = solution.trim().split(" ");		
		for(int i = 0; i < solutionParts.length; i++) {
			int domIdx = Integer.parseInt(solutionParts[i]);
			converter.setGroundAtomState(state, i, domIdx);
		}
		
		// clean up
		new File(this.wcspFilename).delete();
				
		return getResults(queries);
	}

	@Override
	public PossibleWorld getSolution() {
		return state;
	}

}
