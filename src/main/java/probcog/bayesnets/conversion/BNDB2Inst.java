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
package probcog.bayesnets.conversion;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Map.Entry;

import probcog.bayesnets.core.BNDatabase;



/**
 * converts a Bayesian network evidence database given in .bndb format to the .inst format
 * that is, for example, used by ACE and Samiam
 * @author Dominik Jain
 */
public class BNDB2Inst {

	public static void convert(BNDatabase db, File instFile) throws FileNotFoundException {
		try (PrintStream out = new PrintStream(instFile)) {
			out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			out.println("<instantiation>");
			for(Entry<String,String> e : db.getEntries()) {
				out.printf("<inst id=\"%s\" value=\"%s\"/>\n", e.getKey(), e.getValue());
			}
			out.println("</instantiation>");
		}
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("usage: bndb2inst <.bndb input file> <.inst output filename>");
			return;
		}
		
		BNDatabase db = new BNDatabase(new File(args[0]));
		convert(db, new File(args[1]));
	}

}
