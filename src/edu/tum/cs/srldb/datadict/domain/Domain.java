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
	
	public boolean isBoolean() {
		if(!isFinite())
			return false;
		String[] values = getValues();
		BooleanDomain bd = BooleanDomain.getInstance();
		if(values.length >= 1 && values.length <= 2) {
			for(String v : values)
				if(!bd.containsString(v))
					return false;					
			return true;
		}
		return false;
	}
}
