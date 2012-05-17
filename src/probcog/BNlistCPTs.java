package probcog;
import java.io.PrintStream;
import java.util.HashMap;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.core.BeliefNetworkEx.CPTWalker;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;


public class BNlistCPTs {
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		String bnFile = null;
		boolean onlyNonZero = false;
		for(String a : args) {
			if(a.charAt(0) == '-') {
				if(a.equals("-nz"))
					onlyNonZero = true;
			}
			else
				bnFile = a;
		}
		
		if(bnFile == null) {
			System.out.println("\nprints a network's CPTs to stdout");
			System.out.println("\n  usage: BNlistCPTs [options] <Bayesian network file>");
			System.out.println("\n  options: -nz  print only non-zero entries\n");
			return;
		}
		
		BeliefNetworkEx bn = new BeliefNetworkEx(bnFile);
		Walker walker = new Walker(bn, onlyNonZero);
		
		for(BeliefNode node : bn.bn.getNodes()) {			
			bn.walkCPT(node, walker, true);			
		}
	}
	
	public static class Walker implements CPTWalker {
		
		protected PrintStream out = System.out;
		protected int domSize, i;
		protected String condition;
		protected BeliefNode[] domProd;
		protected HashMap<BeliefNode, Integer> domLengths;
		protected boolean onlyNonZero;
		
		public Walker(BeliefNetworkEx bn, boolean onlyNonZero) {
			this.onlyNonZero = onlyNonZero;
			domLengths = new HashMap<BeliefNode, Integer>();
			for(BeliefNode node : bn.getNodes()) {
				int max_length = 0;
				Discrete dom = (Discrete)node.getDomain();
				for(int i = 0; i < dom.getOrder(); i++) {
					max_length = Math.max(max_length, dom.getName(i).length());
				}
				domLengths.put(node, max_length);
			}			
		}

		@Override
		public void tellNode(BeliefNode node) {
			domProd = node.getCPF().getDomainProduct();
			out.println("\n" + node.getName() + ":");
		}

		@Override
		public void tellSize(int childConfigs, int parentConfigs) {
			i = 0;
			domSize = childConfigs;			
		}

		@Override
		public void tellValue(int[] addr, double value) {			
			if(i % domSize == 0) {
				System.out.println("--");
				StringBuffer sb = new StringBuffer();
				for(int k = 1; k < domProd.length; k++) {
					sb.append(" ");
					sb.append(domProd[k].getName()).append('=').append(String.format(String.format("%%-%ds", domLengths.get(domProd[k])), domProd[k].getDomain().getName(addr[k])));
				}
				condition = sb.toString();
			}
			
			if(!onlyNonZero || value != 0) {
				out.printf("%.6f  %s=%s |%s\n", 
							value, 
							domProd[0].getName(), 
							String.format(String.format("%%-%ds", domLengths.get(domProd[0])), domProd[0].getDomain().getName(addr[0])), condition);
			}
			
			i++;
		}		
	}
}
