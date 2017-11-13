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
	
	protected String format(String format, Object ... args) {
		if (args.length == 0)
			return format;
		else
			return String.format(format, args);
	}
	
	public void debug(String format, Object ... args) {
		printDebug(format, args);
		logDebug(format, args);
	}

	protected void logDebug(String format, Object... args) {
		if (logger.isDebugEnabled()) {
			logger.debug(format(format, args));
		}
	}
	
	protected void printDebug(String format, Object ... args) {
		if (printer.getVerboseMode() && printer.getDebugMode()) {
			System.out.println(format(format, args));
		}
	}
	
	public void trace(String format, Object ... args) {
		printTrace(format, args);
		logTrace(format, args);
	}

	protected void logTrace(String format, Object... args) {
		if (logger.isTraceEnabled()) {
			logger.trace(format(format, args));
		}
	}
	
	protected void printTrace(String format, Object ... args) {
		String s = format(format, args);
		printDebug(s);
	}

	public void info(String format, Object ... args) {
		printInfo(format, args);
		logInfo(format, args);
	}

	protected void logInfo(String format, Object... args) {
		if (logger.isInfoEnabled()) {
			logger.info(format(format, args));
		}
	}

	protected void printInfo(String format, Object ... args) {
		if (printer.getVerboseMode()) {
			System.out.println(format(format, args));
		}
	}
	
	public void warn(String format, Object ... args) {
		printWarn(format, args);
		logWarn(format, args);
	}

	protected void logWarn(String format, Object... args) {
		if (logger.isWarnEnabled()) {
			logger.warn(format(format, args));
		}
	}

	protected void printWarn(String format, Object ... args) {
		if (printer.getVerboseMode()) {
			System.err.println(format(format, args));
		}
	}
	
	/**
	 * Prints and logs the given message at the given level
	 * @param level the level
	 * @param s the message
	 */
	public void out(Level level, String format, Object ... args) {
		switch(level) {
		case DEBUG:
			debug(format, args);
			break;
		case INFO:
			info(format, args);
			break;
		case TRACE:
			trace(format, args);
			break;
		case WARN:
			warn(format, args);
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
		print(printLevel, format, args);
		log(logLevel, format, args);
	}

	public void log(Level logLevel, String format, Object ... args) {
		switch (logLevel) {
		case DEBUG:
			logDebug(format, args);
			break;
		case INFO:
			logInfo(format, args);
			break;
		case TRACE:
			logTrace(format, args);
			break;
		case WARN:
			logWarn(format, args);
			break;
		default:
			throw new RuntimeException("Unhandled level " + logLevel);
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
			throw new RuntimeException("Unhandled level " + printLevel);
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
