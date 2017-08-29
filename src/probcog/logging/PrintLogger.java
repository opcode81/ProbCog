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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintLogger {
	protected Logger logger;
	protected VerbosePrinter printer;
	
	private static class PreconfiguredPrinter implements VerbosePrinter {
		private boolean verboseMode;
		private boolean debugMode;

		private PreconfiguredPrinter(boolean verboseMode, boolean debugMode) {
			this.verboseMode = verboseMode;
			this.debugMode = debugMode;
		}

		@Override
		public boolean getVerboseMode() {
			return verboseMode;
		}

		@Override
		public boolean getDebugMode() {
			return debugMode;
		}
	}

	public PrintLogger(Class<?> cls, boolean verboseMode, boolean debugMode) {
		this.logger = LoggerFactory.getLogger(cls);
		this.printer = new PreconfiguredPrinter(verboseMode, debugMode);
	}
	
	public PrintLogger(VerbosePrinter printer) {
		this.logger = LoggerFactory.getLogger(printer.getClass());
		this.printer = printer;
	}
	
	public void printDebug(String s) {
		if (printer.getVerboseMode() && printer.getDebugMode()) {
			System.out.println(s);
		}
		logger.debug(s);
	}
	
	public void printTrace(String s) {
		if (printer.getVerboseMode() && printer.getDebugMode()) {
			System.out.println(s);
		}
		logger.trace(s);
	}
	
	public void printInfo(String s) {
		if (printer.getVerboseMode()) {
			System.out.println(s);
		}
		logger.info(s);
	}
	
	public void printWarn(String s) {
		if (printer.getVerboseMode()) {
			System.err.println(s);
		}
		logger.warn(s);
	}
}
