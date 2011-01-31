package edu.tum.cs.bayesnets.conversion;
import java.io.PrintStream;

import edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader;
import edu.ksu.cis.bnj.ver3.streams.Exporter;
import edu.ksu.cis.bnj.ver3.streams.Importer;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;


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
