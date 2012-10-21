/*******************************************************************************
 * Copyright (C) 2007-2012 Dominik Jain.
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
package probcog.srl.mln;

import java.io.PrintStream;

import probcog.srl.Signature;

import edu.tum.cs.util.StringTool;

/**
 * Helper class for the output of MLN files.
 * @author Dominik Jain
 */
public class MLNWriter {
	protected java.io.PrintStream out;
	
	public MLNWriter(PrintStream out) {
		this.out = out;
	}
	
	public void writeDomainDecl(String domName, Iterable<String> elems) {
		out.printf("%s = {%s}\n", formatAsTypeName(domName), StringTool.join(", ", elems));
	}
	
	public void writePredicateDecl(String predName, String[] types, Integer functionallyDeterminedArg) {
		out.print(formatAsPredName(predName));
		out.print('(');
		for(int i = 0; i < types.length; i++) {
			if(i > 0)
				out.print(", ");
			out.print(formatAsTypeName(types[i]));
			if(functionallyDeterminedArg != null && i == functionallyDeterminedArg)
				out.print("!");			
		}
		out.println(')');
	}
	
	public void writePredicateDecl(Signature sig, Integer functionallyDeterminedArg) {
		writePredicateDecl(sig.functionName, sig.argTypes, functionallyDeterminedArg);
	}
	
	/**
	 * @deprecated mutual exclusiveness and exhaustiveness is now declared directly in the predicate declaration 
	 * @param predName
	 * @param params
	 * @param detParams parameters that are functionally determined by the others
	 */
	public void writeMutexDecl(String predName, String[] params, String[] detParams) {
		out.print(formatAsPredName(predName));
		out.print('(');
		for(int i = 0; i < params.length; i++) {
			if(i > 0) out.print(", ");
			out.print("a" + i);
			for(int j = 0; j < detParams.length; j++)
				if(params[i].equals(detParams[j])) {
					out.print("!");
					break;
				}					
		}
		out.println(')');
	}
	
	public static String formatAsPredName(String predName) {
		return lowerCaseString(predName);
	}
	
	public static String formatAsTypeName(String typeName) {
		return lowerCaseString(typeName);
	}
	
	public static String formatAsAtom(String atom) {
		int pos = atom.indexOf('(');
		String predName = atom.substring(0, pos);
		String params = atom.substring(pos);
		return formatAsPredName(predName) + params;
	}
	
	public static String formatAsConstant(String constant) {
		return upperCaseString(constant);
	}
	
	/**
	 * returns a string where the first letter is lower case
	 * @param s the string to convert
	 * @return the string s with the first letter converted to lower case
	 */
	public static String lowerCaseString(String s) { 
		char[] c = s.toCharArray();
		c[0] = Character.toLowerCase(c[0]);
		return new String(c);
	}

	/**
	 * returns a string where the first letter is upper case
	 * @param s the string to convert
	 * @return the string s with the first letter converted to upper case
	 */
	public static String upperCaseString(String s) { 
		char[] c = s.toCharArray();
		c[0] = Character.toUpperCase(c[0]);
		return new String(c);
	}

}
