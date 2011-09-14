/*
 * Created on Oct 6, 2010
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets;

import java.util.Vector;

public enum CombiningRule {
	NoisyOr("noisy-or", true) {		
		@Override
		public double compute(Vector<Double> values) {
			double prod = 0.0;
			for(Double v : values)
				prod += Math.log(1.0 - v);			
			return 1.0 - Math.exp(prod);
		}		
	},
	
	NoisyAnd("noisy-and", true) {		
		@Override
		public double compute(Vector<Double> values) {
			double prod = 1.0;
			for(Double v : values)
				prod *= v;
			return prod;
		}		
	},
	
	Average("average", false) {		
		@Override
		public double compute(Vector<Double> values) {
			double sum = 0.0;
			for(Double v : values)
				sum += v;			
			return sum / values.size();
		}		
	},

	Maximum("max", true) {		
		@Override
		public double compute(Vector<Double> values) {
			double max = 0.0;
			for(Double v : values)
				max = Math.max(max, v);			
			return max;
		}		
	},

	Minimum("min", true) {		
		@Override
		public double compute(Vector<Double> values) {
			double min = 1.0;
			for(Double v : values)
				min = Math.min(min, v);			
			return min;
		}		
	},
	
	NoisyOrNormalized("noisy-or-n", false) {
		@Override
		public double compute(Vector<Double> values) {
			double prod = 0.0;
			for(Double v : values)
				prod += Math.log(1.0 - v);	
			return 1.0 - Math.exp(prod);
		}
	},

	NoisyAndNormalized("noisy-and-n", false) {
		@Override
		public double compute(Vector<Double> values) {
			double prod = 1.0;
			for(Double v : values)
				prod *= v;
			return prod;
		}		
	};			

	public String stringRepresention;
	public boolean booleanSemantics;
	
	private CombiningRule(String stringRepresentation, boolean booleanSemantics) {
		this.stringRepresention = stringRepresentation;
		this.booleanSemantics = booleanSemantics;
	}	
	
	public static CombiningRule fromString(String s) {
		for(CombiningRule r : CombiningRule.values())
			if(r.stringRepresention.equals(s))
				return r;
		throw new IllegalArgumentException("No such combining rule");
	}
	
	public abstract double compute(Vector<Double> values);
}
