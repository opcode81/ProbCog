/*******************************************************************************
 * Copyright (C) 2012 Dominik Jain.
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
import java.io.PrintStream;

import probcog.srl.Database;
import probcog.srl.directed.bln.BayesianLogicNetwork;


public class BLOGDB2MLNDB {

	public static void main(String[] args) throws Exception {
		if(args.length != 3) {
			System.out.println("usage: BLNDB2MLNDB <bln decls file> <blogdb file> <mln db output file>");
			return;
		}
		String declsFile = args[0];
		String blogdbFile = args[1];
		String outFile = args[2];
		
		BayesianLogicNetwork bln = new BayesianLogicNetwork(declsFile);
		Database db = new Database(bln);
		db.readBLOGDB(blogdbFile);
		db.writeMLNDatabase(new PrintStream(new File(outFile)));
	}

}
