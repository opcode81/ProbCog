/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
import java.io.File;

import probcog.srl.directed.ABLModel;
import probcog.srl.directed.RelationalBeliefNetwork;
import probcog.srl.directed.bln.BayesianLogicNetwork;
import probcog.srl.mln.MarkovLogicNetwork;



public class BLN2MLN {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if(args.length < 4) {
				System.out.println("\n usage: BLN2MLN <declarations file> <network file> <logic file> <output file> [options] \n\n" + 
						             "        -cf  write compact formulas\n" 
						          );
				return;
			}			
			boolean compact = false;
			for(int i = 4; i < args.length; i++) {
				if(args[i].equals("-cf"))
					compact = true;
				else {
					System.err.println("unknown option " + args[i]);
					return;
				}
			}
			BayesianLogicNetwork bln = new BayesianLogicNetwork(args[0], args[1], args[2]);
			String outfile = args[3];
			System.out.println("converting...");
			MarkovLogicNetwork mln = bln.toMLN(compact);
			System.out.printf("saving MLN to %s...\n", outfile);
			mln.write(new File(outfile));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
