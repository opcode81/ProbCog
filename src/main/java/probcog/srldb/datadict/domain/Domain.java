/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
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
package probcog.srldb.datadict.domain;

import java.io.Serializable;

import kdl.prox3.dbmgr.DataTypeEnum;

/**
 * Represents a domain.
 * @author Dominik Jain
 *
 * @param <T> the value type.
 */
public abstract class Domain<T> implements Serializable {
	private static final long serialVersionUID = 1L;
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
		if (this instanceof BooleanDomain)
			return true;
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
