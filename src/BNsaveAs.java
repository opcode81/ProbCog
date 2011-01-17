import edu.tum.cs.bayesnets.core.BeliefNetworkEx;


public class BNsaveAs {
	public static void main(String[] args) throws Exception {
		if(args.length != 2) {
			System.err.println("usage: BNsaveAs <bn file> <bn new file>");
			return;
		}
		
		BeliefNetworkEx bn = new BeliefNetworkEx(args[0]);
		bn.save(args[1]);
	}
}
