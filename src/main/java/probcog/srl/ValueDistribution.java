/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain.
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
package probcog.srl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

/**
 * Represents a distribution over values for a soft variable.
 * @author Dominik Jain
 */
public class ValueDistribution {
	HashMap<String, Double> values = new HashMap<String, Double>();
	
	public void setValue(String domElem, Double value) {
		values.put(domElem, value);			
	}
	
	public double getValue(String domElem) {
		return values.get(domElem);
	}
	
	public Collection<String> getDomainElements() {
		return values.keySet();
	}
	
	public String getSingleValue() {
		if (values.size() != 1 || !values.values().iterator().next().equals(Double.valueOf(1.0)))
			return null;
		return values.keySet().iterator().next();
	}
	
	public Set<Entry<String,Double>> entrySet() {
		return values.entrySet();
	}
}