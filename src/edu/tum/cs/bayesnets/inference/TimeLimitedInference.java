/*
 * Created on Nov 2, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.inference;

import java.util.Vector;

import edu.tum.cs.inference.BasicSampledDistribution;
import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.inference.BasicSampledDistribution.DistributionComparison;
import edu.tum.cs.inference.BasicSampledDistribution.DistributionEntryComparison;
import edu.tum.cs.inference.BasicSampledDistribution.MeanSquaredError;
import edu.tum.cs.util.Stopwatch;

public class TimeLimitedInference implements IParameterHandler {

	protected ITimeLimitedInference inference;
	protected double time, interval;
	protected InferenceThread thread;
	protected BasicSampledDistribution referenceDistribution = null;
	/**
	 * mean-squared errors
	 */	
	protected Vector<Double> MSEs = null;
	protected Vector<Class<? extends DistributionEntryComparison>> comparisonClasses;
	protected ParameterHandler paramHandler;
	protected boolean verbose = true;
	protected int[] evidenceDomainIndices = null;

	public TimeLimitedInference(ITimeLimitedInference inference, double time, double interval) throws Exception {
		this.inference = inference;
		this.time = time;
		this.interval = interval;
		comparisonClasses = new Vector<Class<? extends DistributionEntryComparison>>();
		paramHandler = new ParameterHandler(this);
		paramHandler.add("verbose", "setVerbose");
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
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
			int numSteps = (int)(time / interval);
			for(int i = 1; i <= numSteps && thread.isAlive(); i++) {							
				Thread.sleep((int)(1000*interval));
				if(verbose) System.out.printf("polling results after %fs (interval %d)...\n", sw.getElapsedTimeSecs(), i);
				SampledDistribution dist = pollResults(true);
				if(verbose && dist != null) System.out.printf("%d samples taken\n", dist.steps);
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
	
	/**
	 * sets evidence domain indices for distribution comparison (in order to be able to ignore
	 * evidence variables in the comparisons)
	 * @param evidenceDomainIndices
	 */
	public void setEvidenceDomainIndices(int[] evidenceDomainIndices) {
		this.evidenceDomainIndices = evidenceDomainIndices;
	}
	
	protected DistributionComparison doComparison(BasicSampledDistribution dist) throws Exception {
		DistributionComparison dc = new DistributionComparison(this.referenceDistribution, dist);
		for(Class<? extends DistributionEntryComparison> c : comparisonClasses) 
			dc.addEntryComparison(c);
		dc.compare(this.evidenceDomainIndices);		
		dc.printResults();
		return dc;
	}
	
	public SampledDistribution pollResults(boolean allowPrint) throws Exception {
		SampledDistribution dist = thread.pollResults();
		if(allowPrint && verbose && dist != null)
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

	@Override
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
}
