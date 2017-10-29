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
	public enum Level {
		WARN, INFO, DEBUG, TRACE
	}
	
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
	
	public void debug(String s) {
		printDebug(s);
		logger.debug(s);
	}
	
	protected void printDebug(String s) {
		if (printer.getVerboseMode() && printer.getDebugMode()) {
			System.out.println(s);
		}
	}
	
	public void trace(String s) {
		printTrace(s);
		logger.trace(s);
	}
	
	protected void printTrace(String s) {
		printDebug(s);
	}

	public void info(String s) {
		printInfo(s);
		logger.info(s);
	}

	protected void printInfo(String s) {
		if (printer.getVerboseMode()) {
			System.out.println(s);
		}
	}
	
	public void warn(String s) {
		printWarn(s);
		logger.warn(s);
	}

	protected void printWarn(String s) {
		if (printer.getVerboseMode()) {
			System.err.println(s);
		}
	}
	
	/**
	 * Prints and logs the given message at the given level
	 * @param level the level
	 * @param s the message
	 */
	public void out(Level level, String s) {
		switch(level) {
		case DEBUG:
			debug(s);
			break;
		case INFO:
			info(s);
			break;
		case TRACE:
			trace(s);
			break;
		case WARN:
			warn(s);
			break;
		default:
			throw new RuntimeException("Unhandled level");
		}
	}
	
	/**
	 * Prints and logs the given message at potentially different levels
	 * @param printLevel the level for printing
	 * @param logLevel the level for logging
	 * @param s the message
	 */
	public void out(Level printLevel, Level logLevel, String s) {
		print(printLevel, s);
		log(logLevel, s);
	}

	public void log(Level logLevel, String s) {
		switch (logLevel) {
		case DEBUG:
			logger.debug(s);
			break;
		case INFO:
			logger.info(s);
			break;
		case TRACE:
			logger.trace(s);
			break;
		case WARN:
			logger.warn(s);
			break;
		default:
			throw new RuntimeException("Unhandled level");
		}
	}

	public void print(Level printLevel, String s) {
		switch (printLevel) {
		case DEBUG:
			printDebug(s);
			break;
		case INFO:
			printInfo(s);
			break;
		case TRACE:
			printTrace(s);
			break;
		case WARN:
			printWarn(s);
			break;
		default:
			throw new RuntimeException("Unhandled level");
		}
	}
	
	/**
	 * @return true if debug-level information is printed, false otherwise
	 */
	public boolean isDebugPrinted() {
		return printer.getVerboseMode() && printer.getDebugMode();
	}
	
	/**
	 * @return true if debug-level information is logged or printed, false otherwise
	 */
	public boolean isDebugEnabled() {
		return logger.isDebugEnabled() || isDebugPrinted();
	}
}
