/*
 * Created on Nov 16, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.inference;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

import umontreal.iro.lecuyer.probdist.BetaDist;

public abstract class BasicSampledDistribution implements IParameterHandler {
	/**
	 * an array of values representing the distribution, one for each node and each domain element:
	 * values[i][j] is the value for the j-th domain element of the i-th node in the network
	 */
	public double[][] values = null;
	/**
	 * the normalization constant that applies to each of the distribution values
	 */
	public Double Z = null;
	/**
	 * the confidence level for the computation of confidence intervals
	 * if null, no confidence interval computations are carried out
	 */
	public Double confidenceLevel = null;
	public ParameterHandler paramHandler;
	
	public BasicSampledDistribution() throws Exception {
		paramHandler = new ParameterHandler(this);
		paramHandler.add("confidenceLevel", "setConfidenceLevel");
	}
	
	public double getProbability(int varIdx, int domainIdx) {
		return values[varIdx][domainIdx] / Z;
	}
	
	/**
	 * constructs a new array with the normalized distribution over values for a variable
	 * @param varIdx index of the variable whose distribution to generate
	 * @return
	 */
	public double[] getDistribution(int varIdx) {
		double[] ret = new double[values[varIdx].length];
		for(int i = 0; i < ret.length; i++)
			ret[i] = values[varIdx][i] / Z;
		return ret;
	}
	
	public void print(PrintStream out) {
		for(int i = 0; i < values.length; i++) {
			printVariableDistribution(out, i);
		}
	}
	
	public abstract Integer getNumSamples();
	
	public void printVariableDistribution(PrintStream out, int idx) {
		out.println(getVariableName(idx) + ":");
		String[] domain = getDomain(idx);
		for(int j = 0; j < domain.length; j++) {
			double prob = values[idx][j] / Z;
			if(confidenceLevel == null) 
				out.printf("  %.4f %s\n", prob, domain[j]);
			else {
				out.printf("  %.4f  %s  %s", prob, getConfidenceInterval(idx, j).toString());				
			}
		}
	}
	
	public ConfidenceInterval getConfidenceInterval(int varIdx, int domIdx) {
		return new ConfidenceInterval(varIdx, domIdx);
	}
	
	public abstract String getVariableName(int idx);
	public abstract int getVariableIndex(String name);
	public abstract String[] getDomain(int idx);
	
	public int getDomainSize(int idx) {
		return values[idx].length;
	}
	
	public GeneralSampledDistribution toGeneralDistribution() throws Exception {
		int numVars = values.length;
		String[] varNames = new String[numVars];
		String[][] domains = new String[numVars][];
		for(int i = 0; i < numVars; i++) {
			varNames[i] = getVariableName(i);
			domains[i] = getDomain(i);
		}
		return new GeneralSampledDistribution(this.values, this.Z, varNames, domains);
	}
	
	/**
	 * gets the mean squared error of another distribution d, assuming that values of this distribution are correct
	 * @param d the other distribution
	 * @return the mean squared error (averaged across all entries of the distribution)
	 * @throws Exception
	 */
	public double getMSE(BasicSampledDistribution d) throws Exception {
		return compare(new MeanSquaredError(this), d);
	}
	
	public double getHellingerDistance(BasicSampledDistribution d) throws Exception {
		return compare(new HellingerDistance(this), d);
	}
	
	public double compare(DistributionEntryComparison dec, BasicSampledDistribution otherDist) throws Exception {
		DistributionComparison dc = new DistributionComparison(this, otherDist);
		dc.addEntryComparison(dec);
		dc.compare();
		return dec.getResult();
	}
	
	public void setConfidenceLevel(Double confidenceLevel) {
		this.confidenceLevel = confidenceLevel;
	}
	
	public boolean usesConfidenceComputation() {
		return confidenceLevel != null;
	}
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	public class ConfidenceInterval {
		public double lowerEnd, upperEnd;		
		protected int precisionDigits = 4;
		
		public ConfidenceInterval(int varIdx, int domIdx) {
			int numSamples = getNumSamples();
			double p = values[varIdx][domIdx] / Z;
			double alpha = p * numSamples;
			double beta = numSamples - alpha;
			alpha += 1;
			beta += 1;
			double confAlpha = 1-confidenceLevel;
			lowerEnd = BetaDist.inverseF(alpha, beta, precisionDigits, confAlpha/2);
			upperEnd = BetaDist.inverseF(alpha, beta, precisionDigits, 1-confAlpha/2);
			if(p > upperEnd) {
				lowerEnd = BetaDist.inverseF(alpha, beta, precisionDigits, confAlpha);
				upperEnd = 1.0;
			}
			else if(p < lowerEnd) {
				lowerEnd = 0.0;
				upperEnd = BetaDist.inverseF(alpha, beta, precisionDigits, 1-confAlpha);
			}
		}
		
		public double getSize() {
			return upperEnd-lowerEnd;
		}
		
		public String toString() {
			return String.format(String.format("[%%.%df;%%.%df] %%.4f", precisionDigits, precisionDigits), lowerEnd, upperEnd, getSize());
		}
	}
	
	public static class DistributionComparison {
		protected BasicSampledDistribution referenceDist, otherDist;
		protected Vector<DistributionEntryComparison> processors;
		
		public DistributionComparison(BasicSampledDistribution referenceDist, BasicSampledDistribution otherDist) {
			this.referenceDist = referenceDist;
			this.otherDist = otherDist;
			processors = new Vector<DistributionEntryComparison>();
		}
		
		public void addEntryComparison(DistributionEntryComparison c) {
			processors.add(c);
		}
		
		public void addEntryComparison(Class<? extends DistributionEntryComparison> c) throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
			addEntryComparison(c.getConstructor(BasicSampledDistribution.class).newInstance(referenceDist));
		}
		
		/**
		 * compare the (posterior marginal) distributions of the 
		 * non-evidence variables, i.e. variables whose domain indices
		 * in evidenceDomainIndices are < 0 
		 * @param evidenceDomainIndices evidence domain indices, indexed by variable index
		 * @throws Exception
		 */
		public void compare(int[] evidenceDomainIndices) throws Exception {
			for(int i = 0; i < otherDist.values.length; i++) {
				if(evidenceDomainIndices != null && evidenceDomainIndices[i] >= 0)
					continue;
				String varName = otherDist.getVariableName(i);
				int i2 = referenceDist.getVariableIndex(varName);
				if(i2 < 0) 
					throw new Exception("Variable " + varName + " has no correspondence in reference distribution");
				for(int j = 0; j < otherDist.values[i].length; j++) {
					double v1 = referenceDist.getProbability(i2, j);
					double v2 = otherDist.getProbability(i, j);
					for(DistributionEntryComparison p : processors)
						p.process(i, j, otherDist.values[i].length, v1, v2);
				}
			}			
		}
		
		public void compare() throws Exception {
			compare(null);
		}
		
		public void printResults() {
			for(DistributionEntryComparison dec : processors)
				dec.printResult();
		}
		
		public double getResult(Class<? extends DistributionEntryComparison> c) throws Exception {
			for(DistributionEntryComparison p : processors)
				if(c.isInstance(p)) {
					return p.getResult();
				}
			throw new Exception(c.getSimpleName() + " was not processed in this comparison");
		}
	}
		
	public static abstract class DistributionEntryComparison {
		BasicSampledDistribution refDist;
		public DistributionEntryComparison(BasicSampledDistribution refDist) {
			this.refDist = refDist;
		}
		public abstract void process(int varIdx, int domIdx, int domSize, double p1, double p2);
		public abstract double getResult();
		public void printResult() {
			System.out.printf("%s = %s\n", getClass().getSimpleName(), getResult());
		}
	}
	
	public static class MeanSquaredError extends DistributionEntryComparison {
		double sum = 0.0; int cnt = 0;
		public MeanSquaredError(BasicSampledDistribution refDist) {
			super(refDist);
		}
		@Override
		public void process(int varIdx, int domIdx, int domSize, double p1, double p2) {
			++cnt;
			double error = p1-p2;
			error *= error;
			sum += error;
		}
		@Override
		public double getResult() {
			return sum/cnt;
		}
	}
	
	public static class MeanAbsError extends DistributionEntryComparison {
		double sum = 0.0; int cnt = 0;
		public MeanAbsError(BasicSampledDistribution refDist) {
			super(refDist);
		}
		@Override
		public void process(int varIdx, int domIdx, int domSize, double p1, double p2) {
			++cnt;
			double error = Math.abs(p1-p2);
			sum += error;
		}
		@Override
		public double getResult() {
			return sum/cnt;
		}
	}
	
	public static class MaxAbsError extends DistributionEntryComparison {
		double max = 0.0;
		public MaxAbsError(BasicSampledDistribution refDist) {
			super(refDist);
		}
		@Override
		public void process(int varIdx, int domIdx, int domSize, double p1, double p2) {
			double error = Math.abs(p1-p2);
			if(error > max)
				max = error;
		}
		@Override
		public double getResult() {
			return max;
		}
	}
	
	public static class HellingerDistance extends DistributionEntryComparison {
		double BhattacharyyaCoefficient = 0.0;
		double sum = 0.0;
		int numVars = 0;
		public HellingerDistance(BasicSampledDistribution refDist) {
			super(refDist);
		}
		@Override
		public void process(int varIdx, int domIdx, int domSize, double p1, double p2) {
			BhattacharyyaCoefficient += Math.sqrt(p1*p2);
			if(domIdx+1 == domSize) {
				numVars++;
				double Hellinger = Math.sqrt(1.0 - BhattacharyyaCoefficient); 
				sum += Hellinger;
				BhattacharyyaCoefficient = 0;
			}
		}
		@Override
		public double getResult() {
			return sum /= numVars;
		}
	}
	
	public static class ErrorList extends DistributionEntryComparison {
		public ErrorList(BasicSampledDistribution refDist) {
			super(refDist);
		}
		@Override
		public void process(int varIdx, int domIdx, int domSize, double p1, double p2) {
			double error = p1 - p2;
			if(error != 0.0) {
				System.out.printf("%s=%s: %f %f -> %f\n", refDist.getVariableName(varIdx), refDist.getDomain(varIdx)[domIdx], p1, p2, error);
			}
		}
		@Override
		public double getResult() {
			return 0;
		}
		@Override
		public void printResult() {}
	}
}
