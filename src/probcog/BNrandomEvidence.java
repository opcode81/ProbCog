/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain.
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
package probcog;
import java.util.HashSet;
import java.util.Random;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Domain;


/**
 * creates random evidence for a Bayesian network
 * @author Dominik Jain
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
