/*******************************************************************************
 * Copyright (C) 2009-2012 Gregor Wylezich, Dominik Jain and Paul Maier.
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
package probcog.srl.mln.inference;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import probcog.logic.GroundAtom;
import probcog.logic.PossibleWorld;
import probcog.srl.mln.MarkovRandomField;
import probcog.wcsp.WCSP;
import probcog.wcsp.WCSPConverter;


/**
 * Toulbar2 branch & bound inference wrapper.
 * Requires installation of the toulbar2 executable in the system PATH.
 * 
 * @author Gregor Wylezich
 * @author Dominik Jain
 * @author Paul Maier
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
		log.info("Performing WCSP conversion...");
		converter = new WCSPConverter(mrf);
		paramHandler.addSubhandler(converter);
		converter.setCacheConstraints(cache);
		WCSP wcsp = converter.run();
		log.debug("Writing WCSP file to " + wcspFilename);
		wcsp.writeWCSP(new PrintStream(wcspFilename), "WCSPFromMLN");
		return converter;
	}
	
	class Toulbar2Call implements Callable<String> {
		private String solution = null;
		private Process toulbar2Process = null;
		private boolean mustTerminate;
		private boolean isComplete;

		@Override
		public String call() throws Exception {

			String command = "toulbar2 " + wcspFilename + " -s "  + toulbar2Args;
			if (System.getProperty("os.name").contains("Windows")) {
				command = "bash -c \"exec " + command + "\""; // use bash on Windows to fix output problem (no output can be read through standard shell on Win10)
			}
			log.info("Running WCSP solver: " + command);
			toulbar2Process = Runtime.getRuntime().exec(command);
			InputStream s = toulbar2Process.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(s));
			isComplete = false;
			mustTerminate = false;
			while(!mustTerminate) {
				try {
					String l = br.readLine();
					if(l == null)
						break;
					if(l.startsWith("New solution:")) {
						log.debug(l);
						solution = br.readLine();
						log.trace(solution);
					}
					else {
						log.debug(l);
					}
				}
				catch(IOException e) {
					break;
				}			
			}
			log.debug("Inference call/toulbar2 process complete");
			isComplete = true;
			return solution;
		}
		
		public String getSolution() {
			return solution;
		}
		
		public void stop() {
			log.debug("Terminating toulbar2 process");
			destroyProcessTree(toulbar2Process);
			mustTerminate = true;
		}
		
		public boolean isComplete() {
			return isComplete;
		}
	}
	
	private static void destroyProcessTree(Process toulbar2Process) {
		if (System.getProperty("os.name").contains("Windows")) {
			// destroy the bash shell as well as the toulbar2 process
			toulbar2Process.descendants().forEach(handle -> handle.destroyForcibly());
		}
		toulbar2Process.destroyForcibly();
	}
	
	interface InferenceCall {
		String infer() throws Exception;
	}
	
	protected void runInference(InferenceCall call) throws Exception {
		// construct WCSP if necessary
		if(converter == null)
			constructWCSP(this.wcspFilename, false);
		
		// run Toulbar2
		String solution = call.infer();
		
		if(solution == null)
			throw new Exception("No solution was found");
		
		// set evidence (as the WCSP does not contain evidence variables)
		state.setEvidence(mrf.getDb());
		
		// set solution state
		log.debug("WCSP solution: " + solution);
		log.info("Reading solution");
		String[] solutionParts = solution.trim().split(" ");		
		for(int i = 0; i < solutionParts.length; i++) {
			int domIdx = Integer.parseInt(solutionParts[i]);
			converter.setGroundAtomState(state, i, domIdx);
		}
		
		// clean up
		log.debug("Deleting " + wcspFilename);
		new File(this.wcspFilename).delete();
	}
	
	@Override
	public ArrayList<InferenceResult> infer(Iterable<String> queries) throws Exception {
		runInference(() -> new Toulbar2Call().call());
		return getResults(queries);
	}
	
	protected class InferenceThread extends Thread {
		Toulbar2Call toulbar2Call = new Toulbar2Call();
		
		public void run() {
			try {
				log.debug("Inference thread spawned");
				toulbar2Call.call();
			} 
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			finally {
				synchronized (this) {
					notifyAll();
				}
				log.debug("Inference thread completed");
			}
		}
		
		public void signalTermination() {
			toulbar2Call.stop();
		}
	};
	
	public ArrayList<InferenceResult> infer(Iterable<String> queries, long inferenceTimeMs) throws Exception {

		runInference(() -> {
			InferenceThread thread = new InferenceThread();
			thread.start();
			synchronized (thread) {
				thread.wait(inferenceTimeMs);
			}
			if (!thread.toulbar2Call.isComplete()) {
				thread.signalTermination();
				thread.join();
			}
			return thread.toulbar2Call.getSolution();
		});
		return getResults(queries);
	}

	@Override
	public PossibleWorld getSolution() {
		return state;
	}
}
