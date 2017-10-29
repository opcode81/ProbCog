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
/*
 * Created on Aug 3, 2009
 */
import java.io.PrintStream;

import probcog.srl.Database;
import probcog.srl.mln.MarkovLogicNetwork;
import probcog.srl.mln.MarkovRandomField;
import probcog.wcsp.WCSP;
import probcog.wcsp.WCSPConverter;


public class MLN2WCSP {

	public static void main(String[] args) throws Exception {
		if(args.length < 3) {
			System.out.println("usage: MLN2WCSP <MLN file> <evidence database file> <WCSP output file>");
			return;			
		}
		
		MarkovLogicNetwork mln = new MarkovLogicNetwork(args[0]);
		Database db = new Database(mln);
		db.readMLNDB(args[1]);
		MarkovRandomField mrf = mln.ground(db);
		WCSPConverter converter = new WCSPConverter(mrf);
		WCSP wcsp = converter.run();
		wcsp.writeWCSP(new PrintStream(args[2]), "WCSPFromMLN");
	}
}
