/*
 * Created on Oct 26, 2007
 */
package edu.tum.cs.bayesnets.core;

import java.util.Arrays;

/**
 * The <code>DiscretizationFilter</code> interface is used to discretize continuous domains.
 */
public interface DiscretizationFilter {
	/**
	 * Get the filter's possible String output values (the final domain).
	 * @return 				the possible output values.
	 */
	public String[] getOutputValues();
	/**
	 * Get the output value for the given continuous value.
	 * @param continuous	the continuous value.
	 * @return				the discretized output value
	 */
	public String getValueForContinuous(double continuous);
	
	/**
	 * Return an example value in the specified discretization bin. 
	 * @param bin	the discretization bin.
	 * @return		an example value for the discretization bin.
	 */
	public double getExampleValue(int bin);
	
	/**
	 * Get interval boundaries for the bin. The return value should be a list of 2n values containing the
	 * intervals of the bin.
	 * @param bin the bin.
	 * @return the interval boundaries of the bin.
	 */
	public double[] getIntervals(int bin);
	
	/**
	 * Add output values for the discretization filter.
	 * This should add e.g. split points parsed from the outputValue
	 * to recreate the domain from the output Values.
	 * @param outputValue the outputValue to add.
	 */
	public void addOutputValue(String outputValue);
	
	/**
	 * An instance of <code>Default</code> is a default implementation for
	 * {@link DiscretizationFilter} with given split points.
	 */
	public class Default implements DiscretizationFilter {

		/**
		 * The split points for the domain.
		 */
		protected double[] splitPoints;
		/**
		 * The output values.
		 */
		protected String[] outputValues;
		
		/**
		 * Creates an instance of the filter with the given split points.
		 * @param splitPoints	the split points to use.
		 */
		public Default(double[] splitPoints) {
			init(splitPoints);
		}
		
		/**
		 * Initialise with the given split points.
		 * @param splitPoints	The split points to initialize with. 
		 */
		protected void init(double[] splitPoints) {
			this.splitPoints = new double[splitPoints.length];
			System.arraycopy(splitPoints, 0, this.splitPoints, 0, splitPoints.length);
			Arrays.sort(this.splitPoints);
			outputValues = new String[this.splitPoints.length + 1];
			if (this.splitPoints.length == 0) {	// Without splitpoints we have a different outputValue! 
				outputValues[0] = "-inf < && <= inf";
				return;
			}
			outputValues[0]=String.format("<= %.2e", this.splitPoints[0]);
			for (int i = 0; i < this.splitPoints.length - 1; i++) {
				outputValues[i+1] = String.format( "%.2e < && <= %.2e", this.splitPoints[i], this.splitPoints[i+1]);
			}
			outputValues[this.splitPoints.length] = String.format("> %.2e", this.splitPoints[splitPoints.length-1]);
		}
		
		/* (non-Javadoc)
		 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getExampleValue(int)
		 */
		public double getExampleValue(int bin) {
			if (splitPoints.length < bin)
				throw new IllegalArgumentException("Value out of range: "+bin+">"+splitPoints.length);
			if (splitPoints.length == 0)	// If we have the full range take any value
				return 0.0;
			if (bin == 0) {	// For border bins we multiply by 0.9 and 1.1 respectively. 
				return 0.9*splitPoints[0];
			}
			if (bin == splitPoints.length) {
				return 1.1*splitPoints[splitPoints.length-1];
			}
			return 0.5*(splitPoints[bin]+splitPoints[bin-1]); 
		}
		
		/* (non-Javadoc)
		 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getIntervals(int)
		 */
		public double[] getIntervals(int bin) {
			if (splitPoints.length < bin)
				throw new IllegalArgumentException("Value out of range: "+bin+">"+splitPoints.length);
			if (splitPoints.length == 0)
				return new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
			if (bin == 0)	// For border bins we multiply by 0.9 and 1.1 respectively. 
				return new double[] {Double.NEGATIVE_INFINITY, splitPoints[0]};
			if (bin == splitPoints.length)
				return new double[] {splitPoints[splitPoints.length-1], Double.POSITIVE_INFINITY};
			return new double[] {splitPoints[bin-1], splitPoints[bin]};
		}

		/**
		 * Add a split point at the given place.
		 * @param point the split point to add.
		 */
		public void addSplitPoint(double point) {
			double[] newSplitPoints = new double[splitPoints.length+1];
			System.arraycopy(splitPoints, 0, newSplitPoints, 0, splitPoints.length);
			newSplitPoints[splitPoints.length] = point;
			init(newSplitPoints);
		}
		
		/**
		 * Check if the filter contains a split point at the given place.
		 * @param splitPoint	the split point to check for. 
		 * @return				whether there is a split point at the specified place.
		 */
		public boolean containsSplitPoint(double splitPoint) {
			int index = Arrays.binarySearch(splitPoints, splitPoint);
			if (index > splitPoints.length || index < 0 || splitPoints[index]!=splitPoint)
				return false;
			else
				return true;
		}
		
		/* (non-Javadoc)
		 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#addOutputValue(java.lang.String)
		 */
		public void addOutputValue(String outputValue) {
			outputValue = outputValue.trim();
			if (outputValue.equals("-inf < && <= inf")) {
				splitPoints = new double[0];	// We won't have any split points
			} else if (outputValue.startsWith("<=")) {
				double splitPoint = Double.parseDouble(outputValue.substring(2).trim());
				if (!containsSplitPoint(splitPoint))
					addSplitPoint(splitPoint);
			} else if (outputValue.startsWith(">")) {
				double splitPoint = Double.parseDouble(outputValue.substring(1).trim());
				if (!containsSplitPoint(splitPoint))
					addSplitPoint(splitPoint);
			} else if (outputValue.contains("&&")) {
				String[] parts = outputValue.split("&&");
				if (parts.length != 2)
					throw new IllegalArgumentException("Unable to parse output value "+outputValue+"!");
				double splitPoint = Double.parseDouble(parts[0].split("<")[0].trim());
				if (!containsSplitPoint(splitPoint))
					addSplitPoint(splitPoint);
				splitPoint = Double.parseDouble(parts[1].split("<=")[1].trim());
				if (!containsSplitPoint(splitPoint))
					addSplitPoint(splitPoint);
			} else {
				throw new IllegalArgumentException("Unable to parse output value "+outputValue+"!");
			}
		}
		
		/* (non-Javadoc)
		 * @see edu.tum.cs.bayesnets.core.DiscretizationFilter#getOutputValues()
		 */
		public String[] getOutputValues() {
			return outputValues;
		}

		/** 
		 * Get the discretized value.
		 * This is a very simple implementation in O(splitPoints.length).
		 * With binary search it would be possible to find it in O(ld(splitPoints.length)).
		 * @param continuous	the continuous value.
		 * @return				the discretized string value. 
		 */
		public String getValueForContinuous(double continuous) {
			for (int i = 0; i < splitPoints.length; i++) {
				double delta = splitPoints[i];	// Rounding error elimination for integers
				if (i>0) {
					delta = splitPoints[i]-splitPoints[i-1];
				} else if (i < splitPoints.length-1) {
					delta = splitPoints[i+1]-splitPoints[i];
				}
				if (continuous <= splitPoints[i]+delta*1e-5)
					return outputValues[i];
			}
			return outputValues[splitPoints.length];
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return "Discretized(splitPoints = "+Arrays.toString(splitPoints)+"\n\tOutputValues: "+Arrays.toString(outputValues);
		}
	}
}
