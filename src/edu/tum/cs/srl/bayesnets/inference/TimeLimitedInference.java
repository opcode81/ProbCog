/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import java.util.Vector;

import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.util.Stopwatch;

public class TimeLimitedInference {

	protected Sampler inference;
	protected Iterable<String> queries;
	protected double time, interval;
	protected InferenceThread thread;

	public TimeLimitedInference(Sampler inference, Iterable<String> queries, double time, double interval) {
		this.inference = inference;
		this.queries = queries;
		this.time = time;
		this.interval = interval;
	}
	
	public Vector<InferenceResult> run() throws Exception {
		// start the inference thread
		thread = new InferenceThread();
		thread.start();
		// wait, repeatedly polling intermediate results
		Stopwatch sw = new Stopwatch();
		boolean useIntervals = true;
		if(!useIntervals) 
			Thread.sleep((int)(1000*time));
		else {
			while(sw.getElapsedTimeSecs() < time && thread.isAlive()) {
				Thread.sleep((int)(1000*interval));
				pollResults(true); 
			}
		}
		// get final results, terminating the inference thread if it is still running
		Vector<InferenceResult> results = pollResults(false);
		if(thread.isAlive())
			thread.stop();
		return results;
	}
	
	public Vector<InferenceResult> pollResults(boolean print) throws Exception {
		SampledDistribution dist = thread.pollResults();
		Vector<InferenceResult> res = Sampler.getResults(dist, queries);
		if(print) {
			for(InferenceResult r : res)
				r.print();
		}
		return res;
	}

	protected class InferenceThread extends Thread {
		
		public void run() {
			try {
				inference.infer(queries);			
			}
			catch(Exception e) {
				throw new RuntimeException(e);
			}			
		}
		
		public SampledDistribution pollResults() throws Exception {
			return inference.pollResults();
		}
	}
}
