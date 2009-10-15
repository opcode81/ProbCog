/*
 * Created on Oct 15, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.bayesnets.core.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import edu.ksu.cis.bnj.ver3.streams.OmniFormatV1;

/**
 * Importer for the Ergo file format (http://graphmod.ics.uci.edu/group/Ergo_file_format)
 * @author jain
 */
public class Converter_ergo implements edu.ksu.cis.bnj.ver3.streams.Importer {

	public String getDesc() {		
		return "Ergo";
	}

	public String getExt() {
		return "*.erg";
	}

	public void load(InputStream stream, OmniFormatV1 writer) {
		BufferedReader br = new BufferedReader(new InputStreamReader(stream));
		String line;
		try {
			// read preamble
			int numVars = readLineOfInts(br)[0];
			int[] domSizes = readLineOfInts(br);
			int[][] parents = new int[numVars][];
			for(int i = 0; i < numVars; i++) {
				parents[i] = readLineOfInts(br); 
			}
			
			// read probability tables
			line = nextLine(br);
			if(!line.contains("Probabilities"))
				throw new IOException("Expected 'Probabilities' section, got this: " + line);
			double[][] cpfs = new double[numVars][];
			for(int i = 0; i < numVars; i++) {
				int numEntries = readLineOfInts(br)[0];
				int parentConfigs = 1;
				for(int j = 1; j < parents[i].length; j++)
					parentConfigs *= domSizes[parents[i][j]];
				int entriesPerLine = numEntries / parentConfigs;
				double[] cpf = new double[numEntries];
				for(int j = 0; j < parentConfigs; j++) {
					if(readCPF(br, cpf, j*entriesPerLine) != entriesPerLine)
						throw new IOException("CPF line contained unexpected number of entries");
				}
				cpfs[i] = cpf;
			}
			
			// read variable names
			line = nextLine(br);
			if(!line.contains("Names"))
				throw new IOException("Expected 'Names' section, got this: " + line);
			String[] names = new String[numVars];
			for(int i = 0; i < numVars; i++) {
				names[i] = nextLine(br);
			}
			
			// read domain names
			line = nextLine(br);
			if(!line.contains("Labels"))
				throw new IOException("Expected 'Labels' section, got this: " + line);
			String[][] outcomes = new String[numVars][];
			for(int i = 0; i < numVars; i++) {
				outcomes[i] = readLineOfStrings(br);
				if(outcomes[i].length != domSizes[i])
					throw new IOException("Unexpected domain size");
			}
			
			// build the network
			writer.Start();
			writer.CreateBeliefNetwork(0);
			// basic belief node data
			for(int i = 0; i < numVars; i++) {				
				writer.BeginBeliefNode(i);
				writer.SetType("chance");
				for(int j = 0; j < outcomes[i].length; j++)
					writer.BeliefNodeOutcome(outcomes[i][j]);
				writer.SetBeliefNodeName(names[i]);
				writer.EndBeliefNode();
			}
			// rest
			for(int i = 0; i < numVars; i++) {
				// connect parents
				for(int j = 1; j < parents[i].length; j++)
					writer.Connect(parents[i][j], i);
				// cpf
				writer.BeginCPF(i);
				for(int j = 0; j < cpfs[i].length; j++)
					writer.ForwardFlat_CPFWriteValue(Double.toString(cpfs[i][j]));
				writer.EndCPF();
			}
			writer.Finish();
		} 
		catch (IOException e) {
			throw new RuntimeException(e);
		}		
	}

	protected String nextLine(BufferedReader br) throws IOException {
		String l;
		do 
			l = br.readLine().trim();
		while(l.length() == 0);
		return l;
	}
	
	protected String[] readLineOfStrings(BufferedReader br) throws IOException {
		return nextLine(br).split("\\s+");
	}
	
	protected int[] readLineOfInts(BufferedReader br) throws IOException {
		String[] elems = readLineOfStrings(br);
		int[] ret = new int[elems.length];
		for(int i = 0; i < elems.length; i++)
			ret[i] = Integer.parseInt(elems[i]);
		return ret;
	}
	
	protected int readCPF(BufferedReader br, double[] cpf, int i) throws IOException {
		String l = nextLine(br);
		String[] elems = l.split("\\s+");
		for(int j = 0; j < elems.length; j++)
			cpf[i++] = Double.parseDouble(elems[j]);
		return elems.length;
	}
}
