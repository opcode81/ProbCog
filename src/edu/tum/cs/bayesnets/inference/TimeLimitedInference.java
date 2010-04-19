/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import java.util.Vector;

import edu.tum.cs.inference.BasicSampledDistribution;
import edu.tum.cs.inference.BasicSampledDistribution.DistributionComparison;
import edu.tum.cs.inference.BasicSampledDistribution.DistributionEntryComparison;
import edu.tum.cs.inference.BasicSampledDistribution.MeanSquaredError;
import edu.tum.cs.util.Stopwatch;

public class TimeLimitedInference {

	protected ITimeLimitedInference inference;
	protected double time, interval;
	protected InferenceThread thread;
	protected BasicSampledDistribution referenceDistribution = null;
	/**
	 * mean-squared errors
	 */	
	protected Vector<Double> MSEs = null;
	protected Vector<Class<? extends DistributionEntryComparison>> comparisonClasses;

	public TimeLimitedInference(ITimeLimitedInference inference, double time, double interval) {
		this.inference = inference;
		this.time = time;
		this.interval = interval;
		comparisonClasses = new Vector<Class<? extends DistributionEntryComparison>>();
	}
	
	public void setReferenceDistribution(BasicSampledDistribution dist) {
		referenceDistribution = dist;		
		comparisonClasses.add(BasicSampledDistribution.MeanSquaredError.class);
		comparisonClasses.add(BasicSampledDistribution.HellingerDistance.class);
		MSEs = new Vector<Double>();
	}
	
	public SampledDistribution run() throws Exception {
		// start the inference thread
		thread = new InferenceThread();
		thread.start();
		// wait, repeatedly polling intermediate results
		Stopwatch sw = new Stopwatch();
		sw.start();
		boolean useIntervals = true;
		if(!useIntervals) 
			Thread.sleep((int)(1000*time));
		else {
			while(sw.getElapsedTimeSecs() < time && thread.isAlive()) {				
				Thread.sleep((int)(1000*interval));
				System.out.println("polling results after " + sw.getElapsedTimeSecs() + "s...");
				SampledDistribution dist = pollResults(false);
				if(dist != null) System.out.printf("%d samples taken\n", dist.steps);
				if(referenceDistribution != null) {
					double mse;
					if(dist == null)
						mse = Double.POSITIVE_INFINITY;
					else {						
						DistributionComparison dc = doComparison(dist);
						mse = dc.getResult(MeanSquaredError.class);
					}
					MSEs.add(mse);					
				}
			}
		}
		// get final results, terminating the inference thread if it is still running
		SampledDistribution results = pollResults(false);
		if(thread.isAlive())
			thread.stop();
		return results;
	}
	
	protected DistributionComparison doComparison(BasicSampledDistribution dist) throws Exception {
		DistributionComparison dc = new DistributionComparison(this.referenceDistribution, dist);
		for(Class<? extends DistributionEntryComparison> c : comparisonClasses) 
			dc.addEntryComparison(c);
		dc.compare();		
		dc.printResults();
		return dc;
	}
	
	public SampledDistribution pollResults(boolean print) throws Exception {
		SampledDistribution dist = thread.pollResults();
		if(print && dist != null)
			printResults(dist);
		return dist;
	}
	
	protected void printResults(SampledDistribution dist) {
		// TODO
	}
	
	/**
	 * returns the mean squared errors collected after each interval
	 * @return
	 */
	public Vector<Double> getMSEs() {
		return MSEs;
	}

	protected class InferenceThread extends Thread {
		
		public void run() {
			try {
				inference.infer();			
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
