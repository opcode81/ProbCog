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
package probcog.bayesnets.inference;

import java.util.Vector;

import probcog.exception.ProbCogException;
import probcog.inference.BasicSampledDistribution;
import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.inference.BasicSampledDistribution.DistributionComparison;
import probcog.inference.BasicSampledDistribution.DistributionEntryComparison;
import probcog.inference.BasicSampledDistribution.MeanSquaredError;

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

	public TimeLimitedInference(ITimeLimitedInference inference, double time, double interval) throws ProbCogException {
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
	
	public SampledDistribution run() throws ProbCogException {
		// start the inference thread
		thread = new InferenceThread();
		thread.start();
		// wait, repeatedly polling intermediate results
		Stopwatch sw = new Stopwatch();
		sw.start();
		boolean useIntervals = true;
		try {
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
		}
		catch (InterruptedException e) {
			throw new ProbCogException(e);
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
	
	protected DistributionComparison doComparison(BasicSampledDistribution dist) throws ProbCogException {
		DistributionComparison dc = new DistributionComparison(this.referenceDistribution, dist);
		for(Class<? extends DistributionEntryComparison> c : comparisonClasses) 
			dc.addEntryComparison(c);
		dc.compare(this.evidenceDomainIndices);		
		dc.printResults();
		return dc;
	}
	
	public SampledDistribution pollResults(boolean allowPrint) throws ProbCogException {
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
		
		public SampledDistribution pollResults() throws ProbCogException {
			return inference.pollResults();
		}
	}

	@Override
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
}
