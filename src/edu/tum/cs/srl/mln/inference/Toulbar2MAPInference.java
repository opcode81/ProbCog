/*
 * Created on Aug 19, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.mln.inference;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.wcsp.WCSPConverter;

public class Toulbar2MAPInference extends MAPInferenceAlgorithm {
	
	protected PossibleWorld state;

	public Toulbar2MAPInference(MarkovRandomField mrf) throws Exception {
		super(mrf);
		state = new PossibleWorld(mrf.getWorldVariables());
	}

	@Override
	public double getResult(GroundAtom ga) {
		return state.get(ga.index) ? 1.0 : 0.0;
	}

	@Override
	public ArrayList<InferenceResult> infer(Iterable<String> queries, int maxSteps) throws Exception {
		
		// perform conversion to WCSP
		System.out.println("performing WCSP conversion...");
		WCSPConverter converter = new WCSPConverter(mrf);
		converter.run("temp.wcsp", null);
		
		// run Toulbar2
		System.out.println("running Toulbar2...");
		Process p = Runtime.getRuntime().exec("toulbar2 temp.wcsp s");
		InputStream s = p.getInputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(s));
		String solution = null;
		while(true) {
			try {
				String l = br.readLine();
				if(l == null)
					break;
				if(l.startsWith("New solution:")) {
					solution = br.readLine();
				}
				//System.out.println(l);
			}
			catch(IOException e) {
				break;
			}			
		}	
		
		// set solution state
		System.out.println("WCSP solution: " + solution);
		String[] solutionParts = solution.trim().split(" ");		
		for(int i = 0; i < solutionParts.length; i++) {
			int domIdx = Integer.parseInt(solutionParts[i]);
			converter.setGroundAtomState(state, i, domIdx);
		}
		
		// set evidence (as in the WCSP, evidence variables are removed)
		state.setEvidence(mrf.getDb());
		
		// clean up
		new File("temp.wcsp").delete();
				
		return getResults(queries);
	}

	@Override
	public PossibleWorld getSolution() {
		return state;
	}

}
