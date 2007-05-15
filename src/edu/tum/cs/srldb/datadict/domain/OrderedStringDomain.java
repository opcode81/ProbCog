package edu.tum.cs.srldb.datadict.domain;

import kdl.prox3.dbmgr.DataTypeEnum;

/**
 * represents a domain containing string values which are ordered (the order is relevant because the values may be used to label ordered cluster centroids) 
 * @author Dominik Jain
 *
 */
public class OrderedStringDomain extends Domain<String> {

	protected String[] values;
	
	public OrderedStringDomain(String name, String[] values) {
		super(name);
		this.values = values;
	}
	
	@Override
	public boolean contains(String value) {
		for(int i = 0; i < values.length; i++)
			if(values[i].equals(value))
				return true;				
		return false;
	}

	public boolean containsIgnoreCase(String value) {
		for(int i = 0; i < values.length; i++)
			if(values[i].equalsIgnoreCase(value))
				return true;				
		return false;
	}
	
	@Override
	public boolean containsString(String value) {
		return contains(value);
	}
	
	public String[] getValues() {
		return values;
	}
	
	public boolean isFinite() {
		return true;
	}
	
	@Override
	public DataTypeEnum getType() {
		return DataTypeEnum.STR;
	}
}
