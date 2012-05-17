package probcog;
import java.util.HashSet;
import java.util.Random;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Domain;


/**
 * creates random evidence for a Bayesian network
 * @author jain
 */
public class BNrandomEvidence {
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {			
			// read arguments
			if(args.length != 2) {
				System.out.println("\n usage: BNrandomEvidence <network> <numEvidence>\n\n");System.exit(1);
			}			
			String networkFile = args[0];
			int numEvidence = Integer.parseInt(args[1]);

			// load model
			BeliefNetworkEx bn = new BeliefNetworkEx(networkFile);
			BeliefNode[] nodes = bn.bn.getNodes();
			HashSet<BeliefNode> handled = new HashSet<BeliefNode>();
			Random rand = new Random();
			while(handled.size() < numEvidence) {
				BeliefNode n = nodes[rand.nextInt(nodes.length)];
				if(handled.contains(n))
					continue;
				handled.add(n);
				Domain d = n.getDomain();
				System.out.printf("%s = %s\n", n.toString(), d.getName(rand.nextInt(d.getOrder())));
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
