import java.io.File;
import java.io.PrintStream;

import edu.tum.cs.bayesnets.conversion.BN2SRLDB;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srldb.Database;

public class MLN_fromBN_Sprinkler {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			// open Bayesian network
			BeliefNetworkEx bn = new BeliefNetworkEx("../BNJ - October04/sprinkler.xml");
			
			BN2SRLDB bn2db = new BN2SRLDB(bn);
			Database db = bn2db.getDB(20);
			
			// write mln
			String dir = "mln/sampled_bn/";
			db.outputBasicMLN(new PrintStream(new File(dir + "sampled_bn.mln")));
			db.outputMLNDatabase(new PrintStream(new File(dir + "sampled_bn.db")));
			
			bn2db.relearnBN();
			bn.saveXMLBIF(dir + "sampled_bn.bif.xml");
			bn.show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
