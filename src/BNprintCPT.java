import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

/**
 * @author Dominik Jain
 */
public class BNprintCPT {
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		BLNprintCPT.Options options = new BLNprintCPT.Options();
		int i;
		for(i = 0; i < args.length; i++) {
			if(!args[i].startsWith("-"))
				break;
			if(args[i].equals("-firstCol"))
				options.firstDataCol = Integer.parseInt(args[++i]);
			if(args[i].equals("-lastCol"))
				options.lastDataCol = Integer.parseInt(args[++i]);		
			if(args[i].equals("-decimals"))
				options.decimals = Integer.parseInt(args[++i]);
		}
		
		if(args.length != i+2) {
			System.out.println("\nBNprintCPT -- format CPTs for printing using LaTeX\n\n");
			System.out.println("\nusage: BNprintCPT [options] <Bayesian network file: pmml, xml, etc.> <node name>\n\n");
			System.out.println("  options:   -firstCol N    first data column to print (1-based index)\n" + 
			                   "             -lastCol  N    last data column to print (1-based index), followed by dots\n" + 
	           		           "             -decimals N    number of decimals for parameter output (default: 2)\n");
			return;
		}
		
		String fragmentsFile = args[i];
		String nodeName = args[i+1];
		
		BNprintCPT printer = new BNprintCPT(fragmentsFile, options);
		printer.writeCPT(nodeName);
	}
	
	protected BeliefNetworkEx bn;
	protected BLNprintCPT.Options options;
	
	public BNprintCPT(String fragmentsFile, BLNprintCPT.Options options) throws Exception {
		bn = new BeliefNetworkEx(fragmentsFile);	
		this.options = options;
	}
	
	public void writeCPT(String nodeName) throws FileNotFoundException {
		int i = 0;
		for(BeliefNode n : bn.getNodes()) {
			if(n.getName().equals(nodeName)) {
				String filename = String.format("cpt-%s.tex", nodeName, i++);
				System.out.printf("writing %s...\n", filename);
				File f = new File(filename);
				writeCPT(n, new PrintStream(f));
			}
		}
	}
	
	public void writeCPT(BeliefNode node, PrintStream out) {
		BLNprintCPT.Table table = new BLNprintCPT.Table(node.getCPF(), this.options);
		table.writeLatex(out);
	}
}
