package edu.tum.cs.srldb.datadict.domain;

import java.util.Arrays;
import java.util.HashSet;

import kdl.prox3.dbmgr.DataTypeEnum;

/**
 * Domain where the values are obtained automatically. Each time an 
 * attribute value for an attribute with an AutomaticDomain is added to an item,
 * that value is included in the domain. 
 * @author Dominik Jain
 */
public class AutomaticDomain extends Domain<String> {

	protected HashSet<String> values;
	
	public AutomaticDomain(String name) {
		super(name);
		values = new HashSet<String>();
	}
	
	@Override
	public boolean contains(String value) {
		return values.contains(value);
	}	

	@Override
	public boolean containsString(String value) {
		return contains(value);
	}	

	@Override
	public String[] getValues() {
		return values.toArray(new String[values.size()]);
	}

	@Override
	public boolean isFinite() {		
		return true;
	}
	
	public void addValue(String value) {
		values.add(value);
	}
	
	@Override
	public DataTypeEnum getType() {
		return DataTypeEnum.STR;
	}
}
