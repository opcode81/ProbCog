import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.CPT;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.ksu.cis.bnj.ver3.core.Value;
import edu.tum.cs.srl.bayesnets.DecisionNode;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;

/*
 * Created on Sep 28, 2011
 */

public class BLNprintCPT {
	
	public static class Options {
		public Integer firstDataCol = null;
		public Integer lastDataCol = null;
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		Options options = new Options();
		int i;
		for(i = 0; i < args.length; i++) {
			if(!args[i].startsWith("-"))
				break;
			if(args[i].equals("-firstCol"))
				options.firstDataCol = Integer.parseInt(args[++i]);
			if(args[i].equals("-lastCol"))
				options.lastDataCol = Integer.parseInt(args[++i]);			
		}
		
		if(args.length != i+3) {
			System.out.println("\nBLNprintCPTs -- format CPTs for printing using LaTeX\n\n");
			System.out.println("\nusage: BLNprintCPT [options] <bln declarations file> <bln fragment network> <node name>\n\n");
			System.out.println("  options:   -firstCol N    first data column to print (1-based index)\n" + 
					           "             -lastCol  N    last data column to print (1-based index), followed by dots\n");
			return;
		}
		
		String declsFile = args[i];
		String fragmentsFile = args[i+1];
		String nodeName = args[i+2];
		
		BLNprintCPT printer = new BLNprintCPT(declsFile, fragmentsFile, options);
		printer.writeCPT(nodeName);
	}
	
	protected BayesianLogicNetwork bln;
	protected Options options;
	
	public BLNprintCPT(String declsFile, String fragmentsFile, Options options) throws Exception {
		bln = new BayesianLogicNetwork(declsFile, fragmentsFile);	
		this.options = options;
	}
	
	public void writeCPT(String nodeName) throws FileNotFoundException {
		int i = 0;
		for(BeliefNode n : bln.getNodes()) {
			if(n.getName().equals(nodeName)) {
				String filename = String.format("cpt-%s-%d.tex", nodeName, i++);
				System.out.printf("writing %s...\n", filename);
				File f = new File(filename);
				writeCPT(n, new PrintStream(f));
			}
		}
	}
	
	public void writeCPT(BeliefNode node, PrintStream out) {
		// construct no CPF where decision and precondition parents are clamped to true
		RelationalNode rn = bln.getRelationalNode(node);
		HashMap<BeliefNode,Integer> constantSettings = new HashMap<BeliefNode,Integer>();
		HashSet<BeliefNode> excluded = new HashSet<BeliefNode>();
		for(DecisionNode parent : rn.getDecisionParents()) {
			constantSettings.put(parent.node, 0);
			excluded.add(parent.node);
		}
		for(RelationalNode parent : rn.getRelationalParents()) {
			if(parent.isPrecondition) {
				constantSettings.put(parent.node, 0);
				excluded.add(parent.node);
			}
		}		
		Value[] values = AbstractGroundBLN.getSubCPFValues(node.getCPF(), constantSettings);
		
		Vector<BeliefNode> included = new Vector<BeliefNode>();
		CPT cpf = (CPT)node.getCPF();
		BeliefNode[] originalDomProd = cpf.getDomainProduct();		
		for(BeliefNode n : originalDomProd)
			if(!excluded.contains(n))
				included.add(n);		
		BeliefNode[] domprod = included.toArray(new BeliefNode[included.size()]);
		cpf = new CPT(domprod);
		cpf.setValues(values);
		
		Table table = new Table(cpf, this.options);
		table.writeLatex(out);
	}

	public static class Table {
		String[][] table;
		CPF cpf;
		BeliefNode[] domprod;
		int numParents;
		int currentColumn = 1;
		Options options;
		
		public Table(CPF cpf, Options options) {
			this.options = options;
			this.cpf = cpf;
			domprod = cpf.getDomainProduct();
			Domain dom = domprod[0].getDomain();
			int domSize = dom.getOrder();
			int numDistributions = cpf.size() / domSize; 
			int numColumns = numDistributions+1;
			numParents = domprod.length-1;
			int numRows = numParents + domSize;
			table = new String[numRows][numColumns];
			
			// write parent names
			for(int i = 1; i < domprod.length; i++)
				table[i-1][0] = domprod[i].getName();
			
			// write domain
			for(int i = 0; i < domSize; i++)
				table[numParents+i][0] = dom.getName(i);
			
			int[] addr = new int[domprod.length];
			writeData(1, addr);
		}
		
		protected void writeData(int i, int[] addr) {
			Domain dom;
			if(i == addr.length) {
				// write parent configuration
				for(int j = 1; j < domprod.length; j++)
					table[j-1][currentColumn] = domprod[j].getDomain().getName(addr[j]);
			
				// write probabilities
				dom = domprod[0].getDomain();
				int row = domprod.length-1;
				for(int j = 0; j < dom.getOrder(); j++) {
					addr[0] = j;
					double p = cpf.getDouble(addr);
					table[row++][currentColumn] = String.format("%.2f", p);
				}
				
				++currentColumn;
				return;
			}
			
			dom = domprod[i].getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				writeData(i+1, addr);
			}
		}
		
		public void writeLatex(PrintStream out) {
			out.println("\\documentclass{letter}\n\\usepackage[a0paper,landscape]{geometry}\n\\pagestyle{empty}\n\\begin{document}");
			out.print("\\begin{tabular}{|l|");
			printn("l", table[0].length-1, out);
			out.print("|}\n\\hline\n");
			for(int row = 0; row < table.length; row++) {
				
				for(int col = 0; col < table[row].length; col++) {
					
					boolean isDotsCol = false, end = false;
					if(col > 0 && options.firstDataCol != null && col < options.firstDataCol) {
						col = options.firstDataCol;
					}
					if(options.lastDataCol != null && options.lastDataCol == col-1) {
						isDotsCol = true;
						end = true;
					}
					
					if(col > 0) out.print(" & ");
					
					String field = isDotsCol ? "\\dots" : toLatex(table[row][col]); 
					out.print(field == null ? "" : field);
					
					if(end) break;
				}
				out.println("\\\\");
				if(row+1 == numParents)
					out.println("\\hline");
			}
			out.println("\\hline\\end{tabular}\n\\end{document}");
		}
	}
	
	public static void printn(String s, int n, PrintStream out) {
		for(int i = 0; i < n; i++)
			out.print(s);
	}
	
	public static String toLatex(String s) {
		return s.replace("_", "\\_").replace("#", "");
	}
}
