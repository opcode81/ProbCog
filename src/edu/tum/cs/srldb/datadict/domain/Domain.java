package edu.tum.cs.srldb.datadict.domain;

import java.io.Serializable;

import kdl.prox3.dbmgr.DataTypeEnum;

public abstract class Domain<T> implements Serializable {
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
	
	public String toString() {
		StringBuffer buf = new StringBuffer("{");
		int i = 0;
		for(String value : getValues()) {
			if(i++ > 0) buf.append(", ");
			buf.append(value);
		}
		buf.append("}");
		return buf.toString();
	}
}
