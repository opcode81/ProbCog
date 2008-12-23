import java.io.File;
import java.io.PrintStream;

import edu.tum.cs.bayesnets.conversion.BN2SRLDB;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srldb.Database;

public class MLN_fromBN_Player {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			// open Bayesian network
			BeliefNetworkEx bn = new BeliefNetworkEx("bn/player3-learnt.xml");
			
			BN2SRLDB bn2db = new BN2SRLDB(bn);
			Database db = bn2db.getDB(200);
			
			// write mln
			String dir = "mln/bn_players/";
			PrintStream outMLN = new PrintStream(new File(dir + "sampled_bn.mln"));
			db.writeBasicMLN(outMLN);
			bn2db.writeMLNFormulas(outMLN);
			db.writeMLNDatabase(new PrintStream(new File(dir + "sampled_bn.db")));
			
			bn2db.relearnBN();
			bn.saveXMLBIF(dir + "sampled_bn.bif.xml");
			bn.show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
