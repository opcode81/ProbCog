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
 * Represents a domain containing string values which are ordered (the order is relevant 
 * because the values may be used to label ordered cluster centroids) 
 * @author Dominik Jain
 */
public class OrderedStringDomain extends Domain<String> implements Serializable {

	private static final long serialVersionUID = 1L;
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
