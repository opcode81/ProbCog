/*******************************************************************************
 * Copyright (C) 2008-2012 Bernhard Kirchlechner.
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
package probcog.bayesnets.learning;

import weka.filters.Filter;
import weka.filters.supervised.attribute.Discretize;

/**
 * The interface <code>FilterNamer</code> represents objects able to name the
 * domains of filter outputs.
 * 
 * @author Bernhard Kirchlechner
 */
public interface FilterNamer<T extends Filter> {
	/**
	 * Get the domain for the given filter.
	 * 
	 * @param filter
	 *            the filter to extract the domain from.
	 * @return the domain extracted from the filter.
	 */
	public String[] getNames(T filter);

	/**
	 * A default cluster namer, which simply returns the string "~E +/- S" for
	 * each cluster, where E is the expected value and S the standard deviation
	 * of the cluster.
	 * 
	 * @author Bernhard Kirchlechner
	 */
	public static class DefaultDiscretize implements FilterNamer<Discretize> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.tum.cs.bayesnets.learning.FilterNamer#getNames(weka.filters.Filter)
		 */
		public String[] getNames(Discretize filter) {
			double[] cutPoints = filter.getCutPoints(0);
			String[] ret = new String[cutPoints.length + 1];
			ret[0] = String.format("<= %.2e", cutPoints[0]);
			for (int i = 0; i < cutPoints.length - 1; i++) {
				ret[i + 1] = String.format("> %.2e && <= %.2e", cutPoints[i],
						cutPoints[i + 1]);
			}
			ret[cutPoints.length] = String.format("> %.2e",
					cutPoints[cutPoints.length - 1]);
			return ret;
		}
	}
}
