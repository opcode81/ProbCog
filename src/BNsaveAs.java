import edu.tum.cs.bayesnets.core.BeliefNetworkEx;


public class BNsaveAs {
	public static void main(String[] args) throws Exception {
		if(args.length != 2) {			
			System.err.println("usage: BNsaveAs <bn file> <new bn filename>");
			System.err.println("reads and writes any of the supported file formats (format identified by file extensions)");
			return;
		}
		
		BeliefNetworkEx bn = new BeliefNetworkEx(args[0]);
		bn.save(args[1]);
	}
}
