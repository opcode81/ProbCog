/*******************************************************************************
 * Copyright (C) 2017 Dominik Jain.
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
package probcog.logging;

public interface VerbosePrinter {
	/**
	 * Gets the flag indicating whether verbose mode is enabled, which enables
	 * output to be printed to the console
	 * @return true if verbose mode is enabled, false otherwise
	 */
	public boolean getVerboseMode();
	
	/**
	 * Gets the flag indicating whether debug mode is enabled, which prints
	 * additional information to the console
	 * @return true if debug mode is enabled, false otherwise
	 */
	public boolean getDebugMode();
}
