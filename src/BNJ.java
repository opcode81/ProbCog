import java.util.Vector;

import edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;


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
			
			if(pluginDir != null)
				IOPlugInLoader.getInstance().loadPlugins(pluginDir);
			
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
