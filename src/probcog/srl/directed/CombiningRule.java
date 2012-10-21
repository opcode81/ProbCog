/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
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
package probcog.srl.directed;

import java.util.Vector;

/**
 * Combining rules that allow to combine several conditional distributions.
 * @author Dominik Jain
 */
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
