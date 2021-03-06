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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.Callable;

import probcog.exception.ProbCogException;
import probcog.logic.GroundAtom;
import probcog.logic.PossibleWorld;
import probcog.srl.mln.MarkovRandomField;
import probcog.wcsp.WCSP;
import probcog.wcsp.WCSPConverter;


/**
 * A wrapper around the Toulbar2 branch & bound WCSP solver for MPE inference in MLNs.
 * Requires installation of the toulbar2 executable in the system PATH.
 * 
 * @author Gregor Wylezich
 * @author Dominik Jain
 * @author Paul Maier
 */
public class Toulbar2Inference extends MPEInferenceAlgorithm {
	
	protected PossibleWorld state;
	protected String wcspFilename = null;
	protected String toulbar2Args = "";
	protected Long timeLimitMs = null;
	protected boolean keepWCSPFile = false;
	protected File wcspFile;
	private WCSPConverter wcspConverter;

	public Toulbar2Inference(MarkovRandomField mrf) throws ProbCogException {
		super(mrf);
		state = new PossibleWorld(mrf.getWorldVariables());
		this.wcspConverter = new WCSPConverter(mrf);
		paramHandler.addSubhandler(wcspConverter);
		this.paramHandler.add("toulbar2Args", "setToulbar2Args");
		this.paramHandler.add("timeLimitMs", "setTimeLimitMs");
		this.paramHandler.add("keepWCSPFile", boolean.class, b -> { this.keepWCSPFile = b; }, 
				"whether to keep the WCSP file (it is deleted upon completion by default)");
		this.paramHandler.add("wcspFilename", String.class, filename -> { this.wcspFilename = filename; }, 
				"the name of the file to which the WCSP is written to");
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

	protected void writeWCSP(File wcspFile, boolean cache) throws ProbCogException {
		log.info("Writing WCSP to " + wcspFile);
		wcspConverter.setCacheConstraints(cache);
		WCSP wcsp = wcspConverter.run();
		try (PrintStream ps = new PrintStream(wcspFile)) {
			wcsp.writeWCSP(ps, "WCSPFromMLN");
		}
		catch (FileNotFoundException e) {
			throw new ProbCogException("Error writing WCSP content to " + wcspFile, e);
		}
	}
	
	class Toulbar2Call implements Callable<String> {
		private String solution = null;
		private Process toulbar2Process = null;
		private boolean mustTerminate;
		private boolean isComplete;

		@Override
		public String call() throws ProbCogException {
			// generate command
			boolean isWindows = System.getProperty("os.name").contains("Windows");
			boolean useBash = false; // workaround for Windows no longer necessary
			String wcspPath = wcspFile.toString();
			if (useBash && isWindows)
				wcspPath = wcspPath.replace('\\', '/');
			String toulbar2App = "toulbar2";
			if (isWindows)
				toulbar2App += ".exe";
			String command = toulbar2App + " " + wcspPath + " -s " + " -ub=" + wcspConverter.getCostUpperBound() + " " + toulbar2Args;
			if (useBash) {
				// use bash as a wrapper to fix output problems (no toulbar2 output could be read through standard shell on Windows)
				command = "bash -c \"exec " + command + "\""; 
			}
			
			// spawn toulbar2 process
			log.info("Running WCSP solver: " + command);
			try {
				toulbar2Process = Runtime.getRuntime().exec(command);
			} 
			catch (IOException e) {
				throw new ProbCogException(e);
			}
			
			// read toulbar2 output
			BufferedReader brError = new BufferedReader(new InputStreamReader(toulbar2Process.getErrorStream()));
			BufferedReader br = new BufferedReader(new InputStreamReader(toulbar2Process.getInputStream()));
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

			// if there is no solution, read the error output (if any)
			if (solution == null) {
				StringBuilder sb = new StringBuilder();
				while(true) {
					String l;
					try {
						l = brError.readLine();
						if (l == null)
							break;
						sb.append(l);
					}
					catch (IOException e) {
						break;
					}
				}
				String errorOutput = sb.toString();
				if (!errorOutput.isEmpty()) {
					log.warn("Produced no solution with the following error output:\n%s", errorOutput); 
				}
			}
			
			return solution;
		}
		
		public String getSolution() {
			return solution;
		}
		
		public void stop() {
			log.debug("Terminating toulbar2 process");
			toulbar2Process.destroyForcibly();
			mustTerminate = true;
		}
		
		public boolean isComplete() {
			return isComplete;
		}
	}
	
	interface InferenceCall {
		String infer() throws ProbCogException;
	}
	
	/**
	 * Constructs a WCSP from the MRF, makes the toulbar2 inference call and
	 * subsequently writes the result back to the state/possible world
	 * @param call the toulbar2 inference call, which computes the solution as a string with variable value indices
	 * @throws ProbCogException
	 */
	protected void runInference(InferenceCall call) throws ProbCogException {
		// write WCSP
		try {
			this.wcspFile = (wcspFilename != null ? new File(this.wcspFilename) :
				File.createTempFile("probcog-toulbar2-", ".wcsp")).getAbsoluteFile();
		}
		catch (IOException e) {
			throw new ProbCogException(e);
		}
		writeWCSP(wcspFile, false);
		
		try {
			// run Toulbar2
			String solution = call.infer();
			if(solution == null)
				throw new ProbCogException("No solution was found by toulbar2");
			
			// set evidence (as the WCSP does not contain evidence variables)
			state.setEvidence(mrf.getDb());
			
			// set solution state
			log.debug("WCSP solution: " + solution);
			log.info("Reading solution");
			String[] solutionParts = solution.trim().split(" ");		
			for(int i = 0; i < solutionParts.length; i++) {
				int domIdx = Integer.parseInt(solutionParts[i]);
				wcspConverter.setGroundAtomState(state, i, domIdx);
			}
		}
		finally {
			if (!keepWCSPFile) {
				// clean up
				log.info("Deleting " + wcspFile);
				wcspFile.delete();
			}
		}
	}
	
	@Override
	public PossibleWorld inferMPE() throws ProbCogException {
		if (timeLimitMs == null) {
			log.info("Starting toulbar2 inference without a time limit");
			runInference(() -> new Toulbar2Call().call());
		}
		else {
			log.info("Starting toulbar2 inference with time limit: " + timeLimitMs + " ms");
			runInference(() -> {
				// start thread 
				InferenceThread thread = new InferenceThread();
				thread.start();
				
				// wait specified time limit, terminating thread if necessary
				try {
					synchronized (thread) {
						thread.wait(timeLimitMs);
					}
					if (!thread.toulbar2Call.isComplete()) {
						thread.signalTermination();
						thread.join();
					}
				}
				catch (InterruptedException e) {
					throw new ProbCogException(e);
				}
				
				// read result from thread
				return thread.toulbar2Call.getSolution();
			});
		}
		return state;
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
	
	@Override
	public PossibleWorld getSolution() {
		return state;
	}

	public void setTimeLimitMs(Long timeLimitMs) {
		this.timeLimitMs = timeLimitMs;
	}
}
