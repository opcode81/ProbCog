package edu.tum.cs.srldb.datadict.domain;

import kdl.prox3.dbmgr.DataTypeEnum;

public abstract class Domain<T> {
	protected String name;
	
	public Domain(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public abstract boolean contains(T value);
	public abstract boolean containsString(String value);
	public abstract boolean isFinite();
	public abstract String[] getValues();
	public abstract DataTypeEnum getType();
}
