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
	
	public void debug(String format, Object ... args) {
		printDebug(format, args);
		if (logger.isDebugEnabled()) {
			logger.debug(String.format(format, args));
		}
	}
	
	protected void printDebug(String format, Object ... args) {
		if (printer.getVerboseMode() && printer.getDebugMode()) {
			System.out.println(String.format(format, args));
		}
	}
	
	public void trace(String format, Object ... args) {
		printTrace(format, args);
		if (logger.isTraceEnabled()) {
			logger.trace(String.format(format, args));
		}
	}
	
	protected void printTrace(String format, Object ... args) {
		String s = String.format(format, args);
		printDebug(s);
	}

	public void info(String format, Object ... args) {
		printInfo(format, args);
		if (logger.isInfoEnabled()) {
			logger.info(String.format(format, args));
		}
	}

	protected void printInfo(String format, Object ... args) {
		if (printer.getVerboseMode()) {
			System.out.println(String.format(format, args));
		}
	}
	
	public void warn(String format, Object ... args) {
		printWarn(format, args);
		if (logger.isWarnEnabled()) {
			logger.warn(String.format(format, args));
		}
	}

	protected void printWarn(String format, Object ... args) {
		if (printer.getVerboseMode()) {
			System.err.println(String.format(format, args));
		}
	}
	
	/**
	 * Prints and logs the given message at the given level
	 * @param level the level
	 * @param s the message
	 */
	public void out(Level level, String format, Object ... args) {
		String s = String.format(format, args);
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
	public void out(Level printLevel, Level logLevel, String format, Object ... args) {
		String s = String.format(format, args);
		print(printLevel, s);
		log(logLevel, s);
	}

	public void log(Level logLevel, String format, Object ... args) {
		switch (logLevel) {
		case DEBUG:
			logger.debug(format, args);
			break;
		case INFO:
			logger.info(format, args);
			break;
		case TRACE:
			logger.trace(format, args);
			break;
		case WARN:
			logger.warn(format, args);
			break;
		default:
			throw new RuntimeException("Unhandled level");
		}
	}

	public void print(Level printLevel, String format, Object ... args) {
		switch (printLevel) {
		case DEBUG:
			printDebug(format, args);
			break;
		case INFO:
			printInfo(format, args);
			break;
		case TRACE:
			printTrace(format, args);
			break;
		case WARN:
			printWarn(format, args);
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
