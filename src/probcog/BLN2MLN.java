package probcog;
import java.io.File;

import probcog.srl.directed.ABLModel;
import probcog.srl.directed.RelationalBeliefNetwork;
import probcog.srl.directed.bln.BayesianLogicNetwork;
import probcog.srl.mln.MarkovLogicNetwork;



public class BLN2MLN {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if(args.length < 4) {
				System.out.println("\n usage: BLN2MLN <declarations file> <network file> <logic file> <output file> [options] \n\n" + 
						             "        -cf  write compact formulas\n" 
						          );
				return;
			}			
			boolean compact = false;
			for(int i = 4; i < args.length; i++) {
				if(args[i].equals("-cf"))
					compact = true;
				else {
					System.err.println("unknown option " + args[i]);
					return;
				}
			}
			BayesianLogicNetwork bln = new BayesianLogicNetwork(args[0], args[1], args[2]);
			String outfile = args[3];
			System.out.println("converting...");
			MarkovLogicNetwork mln = bln.toMLN();
			System.out.printf("saving MLN to %s...\n", outfile);
			mln.write(new File(outfile));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
