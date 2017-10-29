/*******************************************************************************
 * Copyright (C) 2010 Paul Maier, Dominik Jain.
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
package probcog.bayesnets.conversion;
import java.io.File;
import java.io.PrintStream;
import java.util.Vector;
import java.util.Map.Entry;

import probcog.bayesnets.core.BNDatabase;
import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.core.Domain;


/**
 * converts a Bayesian network evidence database given in .bndb format to the Ergo evidence
 * format (.erg.evid)
 * @author Dominik Jain
 */
public class BNDB2ErgEvid {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
			System.err.println("usage: bndb2ergevid <Bayesian network file> <.bndb file> <.erg.evid file to write to>");
			return;
		}

		BeliefNetworkEx bn = new BeliefNetworkEx(args[0]);
		BNDatabase db = new BNDatabase(new File(args[1]));
		PrintStream out = new PrintStream(new File(args[2]));
		
		out.println("/* Evidence */");
		out.println(db.size());
		Vector<String> ev = new Vector<String>();
		for(Entry<String,String> entry : db.getEntries()) {
			String varName = entry.getKey();
			int nodeIdx = bn.getNodeIndex(varName);
			if(nodeIdx == -1)
				throw new Exception("Node " + varName + " not found in Bayesian network");
			Domain dom = bn.getNode(varName).getDomain();
			String value = entry.getValue();
			int domIdx = -1;
			for(int i = 0; i < dom.getOrder(); i++) {
				if(dom.getName(i).equals(value)) {
					domIdx = i;
					break;
				}					
			}	
			if(domIdx == -1)
				throw new Exception("Value " + value + " not found in domain of " + varName);
			out.println(String.format(" %d %d", nodeIdx, domIdx));
		}
	}
}
