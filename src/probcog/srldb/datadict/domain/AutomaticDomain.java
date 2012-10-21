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
import java.util.HashSet;

import kdl.prox3.dbmgr.DataTypeEnum;

/**
 * Domain where the values are obtained automatically. Each time an 
 * attribute value for an attribute with an AutomaticDomain is added to an item,
 * that value is included in the domain. 
 * @author Dominik Jain
 */
public class AutomaticDomain extends Domain<String> implements Serializable {

	private static final long serialVersionUID = 1L;
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
