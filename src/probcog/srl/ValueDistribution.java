/*
 * Created on Mar 7, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.srl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

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
		if (values.size() != 1 || !values.values().iterator().next().equals(new Double(1.0)))
			return null;
		return values.keySet().iterator().next();
	}
	
	public Set<Entry<String,Double>> entrySet() {
		return values.entrySet();
	}
}