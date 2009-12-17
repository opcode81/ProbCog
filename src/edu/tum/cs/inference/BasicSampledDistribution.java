/*
 * Created on Nov 16, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.inference;

import java.io.PrintStream;
import java.util.Vector;

public abstract class BasicSampledDistribution {
	/**
	 * an array of values representing the distribution, one for each node and each domain element:
	 * values[i][j] is the value for the j-th domain element of the i-th node in the network
	 */
	public double[][] values = null;
	/**
	 * the normalization constant that applies to each of the distribution values
	 */
	public Double Z = null;
	
	public double getProbability(int varIdx, int domainIdx) {
		return values[varIdx][domainIdx] / Z;
	}
	
	public void print(PrintStream out) {
		for(int i = 0; i < values.length; i++) {
			printVariableDistribution(out, i);
		}
	}
	
	public void printVariableDistribution(PrintStream out, int idx) {
		out.println(getVariableName(idx) + ":");
		String[] domain = getDomain(idx);
		for(int j = 0; j < domain.length; j++) {
			double prob = values[idx][j] / Z;
			out.println(String.format("  %.4f %s", prob, domain[j]));
		}
	}
	
	public abstract String getVariableName(int idx);
	public abstract int getVariableIndex(String name);
	public abstract String[] getDomain(int idx);
	
	public int getDomainSize(int idx) {
		return values[idx].length;
	}
	
	public GeneralSampledDistribution toGeneralDistribution() {
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
	 * @return the mean squared error (across all entries of the distribution)
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
		
		public void compare() throws Exception {
			for(int i = 0; i < referenceDist.values.length; i++) {
				String varName = referenceDist.getVariableName(i);
				int i2 = otherDist.getVariableIndex(varName);
				if(i2 < 0) 
					throw new Exception("Variable " + referenceDist.getVariableName(i) + " has no correspondence in second distribution");
				for(int j = 0; j < referenceDist.values[i].length; j++) {
					double v1 = referenceDist.getProbability(i, j);
					double v2 = otherDist.getProbability(i2, j);
					for(DistributionEntryComparison p : processors)
						p.process(i, j, v1, v2);
				}
			}			
		}
		
		public void printResults() {
			for(DistributionEntryComparison dec : processors)
				dec.printResult();
		}
	}
		
	public static abstract class DistributionEntryComparison {
		BasicSampledDistribution refDist;
		public DistributionEntryComparison(BasicSampledDistribution refDist) {
			this.refDist = refDist;
		}
		public abstract void process(int varIdx, int domIdx, double p1, double p2);
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
		public void process(int varIdx, int domIdx, double p1, double p2) {
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
	
	public static class HellingerDistance extends DistributionEntryComparison {
		double BhattacharyyaCoefficient = 0.0;
		double sum = 0.0;
		int prevVarIdx = -1;
		int numVars = 0;
		public HellingerDistance(BasicSampledDistribution refDist) {
			super(refDist);
		}
		@Override
		public void process(int varIdx, int domIdx, double p1, double p2) {
			if(prevVarIdx != varIdx) {
				//if(prevVarIdx >= 0) 
				//	System.out.printf("BC(%s) = %s\n", refDist.getVariableName(prevVarIdx), BhattacharyyaCoefficient);
				prevVarIdx = varIdx;
				numVars++;
				sum += BhattacharyyaCoefficient;
				BhattacharyyaCoefficient = 0;
			}
			BhattacharyyaCoefficient += Math.sqrt(p1*p2);
		}
		@Override
		public double getResult() {
			sum += BhattacharyyaCoefficient;
			sum /= numVars;
			return Math.sqrt(1-sum);
		}
	}
	
	public static class ErrorList extends DistributionEntryComparison {
		public ErrorList(BasicSampledDistribution refDist) {
			super(refDist);
		}
		@Override
		public void process(int varIdx, int domIdx, double p1, double p2) {
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
