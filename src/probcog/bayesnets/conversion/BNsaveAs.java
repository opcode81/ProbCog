/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain, Paul Maier.
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
import java.io.PrintStream;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader;
import edu.ksu.cis.bnj.ver3.streams.Exporter;
import edu.ksu.cis.bnj.ver3.streams.Importer;


public class BNsaveAs {
	public static void main(String[] args) throws Exception {
		BeliefNetworkEx.registerDefaultPlugins();		
		
		if(args.length != 2) {
			PrintStream out = System.err;			
			out.println("\n  usage: BNsaveAs <bn file> <new bn filename>");
			out.println("\nreads and writes any of the supported file formats (format identified by file extensions)");			
			IOPlugInLoader iopl = IOPlugInLoader.getInstance();
			out.println("\nimporters:");
			for(Object o : iopl.getImporters()) {
				Importer imp = (Importer)o;
				out.println("  " + imp.getExt() + ": " + imp.getDesc());
			}
			out.println("\nexporters:");
			for(Object o : iopl.getExporters()) {
				Exporter exp = (Exporter)o;
				out.println("  " + exp.getExt() + ": " + exp.getDesc());
			}
			return;
		}
		
		BeliefNetworkEx bn = new BeliefNetworkEx(args[0]);
		bn.save(args[1]);
	}
}
