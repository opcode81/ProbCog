/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain.
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
package probcog.srl;

/**
 * The standard Boolean variable domain.
 * @author Dominik Jain
 */
public class BooleanDomain {
	public static final String True = "True";
	public static final String False = "False";
	public static final String typeName = "Boolean";
	
	public static boolean isBooleanType(String typeName) {
		return BooleanDomain.typeName.equalsIgnoreCase(typeName); 
	}
	
	public static String getStandardValue(String value) {
		if(value.equalsIgnoreCase(True))
			return True;
		if(value.equalsIgnoreCase(False))
			return False;
		throw new IllegalArgumentException(String.format("'%s' is not a valid representation of a Boolean value {'%s', '%s'}", value, True, False));
	}
}
