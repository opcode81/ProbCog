import edu.tum.cs.srl.bayesnets.BLOGModel;


public class groundBLOG {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if(args.length < 2) {
				System.out.println("\n usage: groundBLOG <BLOG file(s), comma-separated> <XML-BIF file> [-g]\n\n" + 
						             "        -g   guess signatures from network structure rather than obtaining them from the BLOG file(s)\n");
				return;
			}			
			BLOGModel b = new BLOGModel(args[0].split(","), args[1]);
			if(args.length >= 3 && args[2].equals("-g"))
				b.guessSignatures();
			b.getGroundBN().show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
