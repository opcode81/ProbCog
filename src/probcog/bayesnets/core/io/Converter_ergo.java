/*
 * Created on Oct 15, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.bayesnets.core.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.streams.Exporter;
import edu.ksu.cis.bnj.ver3.streams.OmniFormatV1;

/**
 * Importer for the Ergo file format (http://graphmod.ics.uci.edu/group/Ergo_file_format)
 * @author jain
 */
public class Converter_ergo implements edu.ksu.cis.bnj.ver3.streams.Importer, Exporter {

	protected boolean isUAIstyle = false;
	
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
					throw new IOException(String.format("Unexpected domain size: Got %d labels but domain size is %d for variable %s", outcomes[i].length, domSizes[i], names[i]));
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

	@Override
	public void save(BeliefNetwork bn, OutputStream os) {
		BeliefNetworkEx bnex = new BeliefNetworkEx(bn);
		PrintStream out = new PrintStream(os);
		if(isUAIstyle) out.println("BAYES");
		// number of nodes
		BeliefNode[] nodes = bn.getNodes();
		out.println(nodes.length);
		// domain sizes
		for(int i = 0; i < nodes.length; i++)
			out.printf("%d ", nodes[i].getDomain().getOrder());
		out.println();
		// parents
		for(BeliefNode n : nodes) {
			BeliefNode[] domprod = n.getCPF().getDomainProduct();
			out.printf("%d", domprod.length-1);
			for(int i = 1; i <  domprod.length; i++)
				out.printf("\t%d", bnex.getNodeIndex(domprod[i]));
			out.println();
		}
		// CPTs
		if(!isUAIstyle) 
			out.println("\n/* Probabilities */");
		else
			out.println();
		for(BeliefNode n : nodes) {
			CPF cpf = n.getCPF();
			out.println(n.getCPF().size());
			writeTable(out, cpf, 1, new int[cpf.getDomainProduct().length]);
			out.println();
		}
		if(!isUAIstyle) {
			// variable names
			if(!isUAIstyle) out.println("\n/* Names   */");
			for(BeliefNode n : nodes)
				out.println(n.getName());
			// domain entry names
			out.println("\n/* Labels  */");
			for(BeliefNode n : nodes) {
				int order = n.getDomain().getOrder();
				for(int i = 0; i < order; i++) {
					out.printf("%s ", n.getDomain().getName(i));
				}	
				out.println();
			}
		}
	}
	
	public void writeTable(PrintStream out, CPF cpf, int i, int[] addr) {
		BeliefNode[] domprod = cpf.getDomainProduct();
		if(i == domprod.length) {
			for(int d = 0; d < domprod[0].getDomain().getOrder(); d++) {
				addr[0] = d;
				out.printf(" %s", Double.toString(cpf.getDouble(addr)));
			}
			out.println();
			return;
		}
		int order = domprod[i].getDomain().getOrder();
		for(int d = 0; d < order; d++) {
			addr[i] = d;
			writeTable(out, cpf, i+1, addr);
		}
	}
}
