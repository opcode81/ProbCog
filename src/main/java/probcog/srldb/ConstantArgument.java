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
package probcog.srldb;

import java.io.Serializable;

public class ConstantArgument implements IRelationArgument, Serializable {

	private static final long serialVersionUID = 1L;
	protected String constantName;
	
	public ConstantArgument(String constant) {
		constantName = constant;
	}
	
	public String getConstantName() {
		return constantName;
	}
	
	public String toString() {
		return "CONST:" + constantName;
	}

}
