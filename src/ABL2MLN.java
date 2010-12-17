import edu.tum.cs.srl.bayesnets.ABLModel;


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
