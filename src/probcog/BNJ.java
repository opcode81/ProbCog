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
import java.util.Vector;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader;


public class BNJ {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String pluginDir = null;
			Vector<String> files = new Vector<String>();
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-?") || args[i].contains("--help")) {
					System.out.println("usage: bnj [-p <plugin directory>] [network file(s)]");
					return;
				}
				if(args[i].equals("-p")) {
					pluginDir = args[++i];
				}
				else
					files.add(args[i]);
			}

			// load plugins if given directory
			if(pluginDir != null)
				IOPlugInLoader.getInstance().loadPlugins(pluginDir);
			// NOTE: show() loads default plugins
			
			if(files.size() == 0) {
				new BeliefNetworkEx().show(); 			
			}
			else {
				for(String filename : files) {
					new BeliefNetworkEx(filename).show();
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}
