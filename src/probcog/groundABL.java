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


public class groundABL {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if(args.length < 2) {
				System.out.println("\n usage: groundABL <ABL file(s), comma-separated> <XML-BIF file> [-g]\n\n" + 
						             "        -g   guess signatures from network structure rather than obtaining them from the ABL file(s)\n");
				return;
			}			
			ABLModel b = new ABLModel(args[0].split(","), args[1]);
			if(args.length >= 3 && args[2].equals("-g"))
				b.guessSignatures();
			b.getGroundBN().show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
