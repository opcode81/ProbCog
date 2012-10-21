/*******************************************************************************
 * Copyright (C) 2007-2012 Dominik Jain.
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
import probcog.srl.directed.ABLModel;


public class ABL2MLN {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if(args.length < 2) {
				System.out.println("\n usage: BLOG2MLN <BLOG file(s)> <XML-BIF file> [options]\n\n" + 
						             "        -g   guess signatures from network structure rather than obtaining them from the BLOG file(s)\n" +
						             "        -cF  write compact formulas\n" + 
						             "        -nW  numeric weights (rather than formulas such as log(x))\n");
				return;
			}			
			boolean guessSigs = false, compact = false, numericWeights = false;
			for(int i = 2; i < args.length; i++) {
				if(args[i].equals("-g"))
					guessSigs = true;
				else if(args[i].equals("-cF"))
					compact = true;
				else if(args[i].equals("-nW"))
					numericWeights = true;
				else {
					System.err.println("unknown option " + args[i]);
					return;
				}
			}
			ABLModel b = new ABLModel(args[0].split(","), args[1]);
			if(guessSigs)
				b.guessSignatures();
			b.toMLN(System.out, false, compact, numericWeights);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
