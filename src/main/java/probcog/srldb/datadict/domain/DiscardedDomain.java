/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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

import kdl.prox3.dbmgr.DataTypeEnum;

/**
 * Represents a domain that has been discarded.
 * @author Dominik Jain
 */
public class DiscardedDomain extends Domain<String> {

	private DiscardedDomain() {
		super("#unused#");
	}
	
	private static DiscardedDomain singleton = null;
	
	public static DiscardedDomain getInstance() {
		if(singleton != null)
			return singleton;
		return singleton = new DiscardedDomain();
	}

	/**
	 * Comment for <code>serialVersionUID</code>
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public boolean contains(String value) {
		return false;
	}

	@Override
	public boolean containsString(String value) {
		return false;
	}

	@Override
	public DataTypeEnum getType() {
		return null;
	}

	@Override
	public String[] getValues() {
		return new String[0];
	}

	@Override
	public boolean isFinite() {
		return true;
	}

}
