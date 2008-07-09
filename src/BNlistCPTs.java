import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;


public class BNlistCPTs {

	protected static HashMap<BeliefNode, Integer> domLengths;
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.out.println("usage: BNlistCPTs <Bayesian network file>");
			return;
		}
		
		String bnFile = args[0];
		//File f = new File(bnFile + ".csv");
		PrintStream out = System.out; //new PrintStream(f);		
		
		BeliefNetworkEx bn = new BeliefNetworkEx(bnFile);
		
		domLengths = new HashMap<BeliefNode, Integer>();
		for(BeliefNode node : bn.bn.getNodes()) {
			int max_length = 0;
			Discrete dom = (Discrete)node.getDomain();
			for(int i = 0; i < dom.getOrder(); i++) {
				max_length = Math.max(max_length, dom.getName(i).length());
			}
			domLengths.put(node, max_length);
		}
		
		for(BeliefNode node : bn.bn.getNodes()) {
			out.println("\n" + node.getName() + ":");
			
			CPF cpf = node.getCPF();
			walkCPT(cpf, 1, new int[cpf.getDomainProduct().length], out);			
		}
	}

	protected static void walkCPT(CPF cpf, int i, int[] addr, PrintStream out) {
		BeliefNode[] domProd = cpf.getDomainProduct();		
		
		if(i == addr.length) {
			Discrete dom = (Discrete)domProd[0].getDomain();
			String condition = null;
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[0] = j;
				double value = cpf.getDouble(addr);
				if(value != 0) {
					if(condition == null) {
						StringBuffer sb = new StringBuffer();
						for(int k = 1; k < domProd.length; k++) {
							sb.append(" ");
							sb.append(domProd[k].getName()).append('=').append(String.format(String.format("%%-%ds", domLengths.get(domProd[k])), domProd[k].getDomain().getName(addr[k])));
						}
						condition = sb.toString();
					}
					out.printf("%.6f  %s=%s |%s\n", 
								value, 
								domProd[0].getName(), 
								String.format(String.format("%%-%ds", domLengths.get(domProd[0])), domProd[0].getDomain().getName(addr[0])), condition);
				}
			}
			return;
		}
				
		Discrete dom = (Discrete)domProd[i].getDomain();
		for(int j = 0; j < dom.getOrder(); j++) {
			addr[i] = j;
			walkCPT(cpf, i+1, addr, out);
		}
	}
}
