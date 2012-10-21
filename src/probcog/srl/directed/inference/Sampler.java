/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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
package probcog.srl.directed.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;
import java.util.regex.Pattern;

import probcog.bayesnets.inference.SampledDistribution;
import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.srl.directed.bln.AbstractGroundBLN;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.util.Stopwatch;

/**
 * Base class for sampling-based inference methods.
 * @author Dominik Jain
 */
public abstract class Sampler implements IParameterHandler {
	protected boolean debug = false;
	protected boolean verbose = true;
	protected int numSamples = 1000;
	protected int infoInterval = 100;
	protected ParameterHandler paramHandler;
	protected Vector<Integer> queryVars;
	protected Vector<Integer> queryVarQueryIndices;
	protected AbstractGroundBLN gbln;
	double inferenceTime, initTime;
	protected boolean initialized = false;
	
	public Sampler(AbstractGroundBLN gbln) throws Exception {
		this.gbln = gbln;
		paramHandler = new ParameterHandler(this);
		paramHandler.add("maxSteps", "setNumSamples");
		paramHandler.add("numSamples", "setNumSamples");
		paramHandler.add("infoInterval", "setInfoInterval");
		paramHandler.add("debug", "setDebugMode");
		paramHandler.add("verbose", "setVerbose");
	}
		
	/**
	 * return inference results for the queries that were previously specified
	 * @param dist
	 * @return
	 */
	public Vector<InferenceResult> getResults(SampledDistribution dist) {		
		Vector<InferenceResult> results = new Vector<InferenceResult>();
		int j = 0;
		for(Integer i : queryVars) {
			InferenceResult result = new InferenceResult(dist, i);
			result.queryNo = queryVarQueryIndices.get(j); 
			results.add(result);
			++j;
		}
		return results;
	}
	
	public void printResults(SampledDistribution dist) {
		ArrayList<InferenceResult> results = new ArrayList<InferenceResult>(getResults(dist));		
		Collections.sort(results);
		for(InferenceResult res : results)
			res.print();
	}
	
	public double getTotalInferenceTime() {
		return getInitTime() + getInferenceTime();
	}
	
	public double getInferenceTime() {
		return inferenceTime;
	}
	
	public double getInitTime() {
		return initTime;
	}
	
	public void setNumSamples(int n) {
		numSamples = n;
	}
	
	public void setInfoInterval(int n) {
		infoInterval = n;
	}
	
	public final void initialize() throws Exception {
		if(verbose) 
			System.out.println("initializing...");
		Stopwatch sw = new Stopwatch();
		sw.start();
		_initialize();
		initTime = sw.getElapsedTimeSecs();
		initialized = true;
	}
	
	protected void _initialize() throws Exception {		
	}
	
	public SampledDistribution infer() throws Exception {
		// initialization
		if(!initialized)
			initialize();
		// actual inference
		Stopwatch sw = new Stopwatch();
		sw.start();
		SampledDistribution ret = _infer();
		inferenceTime = sw.getElapsedTimeSecs();
		return ret;
	}
	
	protected abstract SampledDistribution _infer() throws Exception;
	
	public Vector<InferenceResult> inferQueries() throws Exception {
		return getResults(infer());
	}
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	public void setQueries(Iterable<String> queries) {
		// generate patterns
		Vector<Pattern> patterns = new Vector<Pattern>();
		for(String query : queries) {
			String p = query;
			p = Pattern.compile("([,\\(])([a-z][^,\\)]*)").matcher(p).replaceAll("$1.*?");
			p = p.replace("(", "\\(").replace(")", "\\)") + ".*";			
			patterns.add(Pattern.compile(p));
			//System.out.println("pattern: " + p);
		}
		
		// check all ground variables for matches
		// TODO This should be done more efficiently by explicitly grounding the requested nodes instead of using pattern matchers
		BeliefNode[] nodes = gbln.getGroundNetwork().getNodes();
		queryVars = new Vector<Integer>();
		queryVarQueryIndices = new Vector<Integer>();
		for(int i = 0; i < nodes.length; i++) {
			int idxQuery = 0;
			for(Pattern pattern : patterns)	{		
				if(pattern.matcher(nodes[i].getName()).matches()) {
					queryVars.add(i);
					queryVarQueryIndices.add(idxQuery);
					break;					
				}
				++idxQuery;
			}
		}
	}
}
