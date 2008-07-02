import java.io.File;
import java.io.PrintStream;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;


public class BN2CSV {
	public static int currentColumn;

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.out.println("usage: BN2CSV <Bayesian network file>");
			return;
		}
		
		String bnFile = args[0];
		File f = new File(bnFile + ".csv");
		PrintStream out = new PrintStream(f);
		
		BeliefNetworkEx bn = new BeliefNetworkEx(bnFile);
		for(BeliefNode node : bn.bn.getNodes()) {
			// get required number of columns
			int columns = 1;
			CPF cpf = node.getCPF();
			BeliefNode[] domProd = cpf.getDomainProduct();
			for(int i = 1; i < domProd.length; i++)
				columns *= domProd[i].getDomain().getOrder();
			columns++; // label column on the left
			
			// get required number of rows
			int numParents = domProd.length-1;
			int domainSize = domProd[0].getDomain().getOrder();
			int rows = domainSize + numParents;
			
			String[][] table = new String[columns][rows];
			
			// leftmost column
			currentColumn = 0;
			int row = 0;
			for(int i = 1; i < domProd.length; i++)
				table[currentColumn][row++] = domProd[i].getName();
			for(int i = 0; i < domainSize; i++)
				table[currentColumn][row++] = node.getDomain().getName(i);
			
			// cpt
			walkCPT(cpf, 1, new int[domProd.length], table);
			
			// write
			out.println("\n" + node.getName());
			for(int r = 0; r < rows; r++) {
				for(int c = 0; c < columns; c++) {
					if(c > 0)
						out.print('\t');
					out.print(table[c][r]);
				}
				out.println();
			}
		}
	}
	
	protected static void walkCPT(CPF cpf, int i, int[] addr, String[][] table) {
		BeliefNode[] domProd = cpf.getDomainProduct();		
		
		if(i == addr.length) {
			currentColumn++;
			int row = 0;
			for(int j = 1; j < domProd.length; j++)
				table[currentColumn][row++] = domProd[j].getDomain().getName(addr[j]);
			Discrete dom = (Discrete)domProd[0].getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[0] = j;
				table[currentColumn][row++] = Double.toString(cpf.getDouble(addr)); 
			}
			return;
		}
				
		Discrete dom = (Discrete)domProd[i].getDomain();
		for(int j = 0; j < dom.getOrder(); j++) {
			addr[i] = j;
			walkCPT(cpf, i+1, addr, table);
		}
	}

}
