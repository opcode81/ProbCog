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
package probcog.inference;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Interface to be implemented for dynamic parameter handling.
 * @author Dominik Jain
 */
public interface IParameterHandler {
	public ParameterHandler getParameterHandler();
	
	/**
	 * Sets a parameter by name.
	 * Any parameter that is supported by this object's parameter handler can be set.
	 * To obtain information on the parameters that are supported, use
	 * {@link #printNamedParameterInfo}.
	 * @param name the name of the parameter
	 * @param value the value (the type of which depends on the concrete parameter)
	 * @throws Exception if the parameter cannot be set
	 */
	public default void setParameterByName(String name, Object value) throws Exception {
		try {
			boolean handled = getParameterHandler().handle(name, value);
			if (!handled)
				throw new Exception("Parameter was not handled");
		} catch (Exception e) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (PrintStream ps = new PrintStream(bos)) {
				getParameterHandler().printHelp(ps);
				throw new Exception("Parameter could not be handled. Supported parameters:\n" + 
						bos.toString());
			}
		}
	}
	
	/**
	 * Prints information on (named) parameters that can be set via {@link #setParameterByName(String, Object)}
	 * to the given stream  
	 * @param out the stream to write to
	 */
	public default void printNamedParameterInfo(PrintStream out) {
		getParameterHandler().printHelp(out);
	}
}
