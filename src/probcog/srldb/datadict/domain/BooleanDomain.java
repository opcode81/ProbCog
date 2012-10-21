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

public class BooleanDomain extends OrderedStringDomain {
	private static final long serialVersionUID = 1L;
	protected static BooleanDomain singleton = null;
	
	public static BooleanDomain getInstance() {
		if(singleton == null)
			singleton = new BooleanDomain();
		return singleton;
	}
	
	protected BooleanDomain() {
		super("bool", new String[]{"true", "false"});		
	}
	
	public boolean isTrue(String value) {
		return value.equalsIgnoreCase(this.values[0]);
	}
	
	public boolean isFalse(String value) {
		return !isTrue(value);
	}
	
	public boolean containsString(String s) {
		return containsIgnoreCase(s);
	}
}
