import edu.tum.cs.bayesnets.core.BeliefNetworkEx;


public class BNJ {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if(args.length == 0) {
				System.out.println("usage: bnj <plugin directory> [xml-bif file(s)]");
				return;
			}
			String pluginDir = args[0];
			if(args.length == 1) {
				new BeliefNetworkEx().show(pluginDir);			
			}
			else {
				for(int i = 1; i < args.length; i++) {
					new BeliefNetworkEx(args[i]).show(pluginDir);
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}
