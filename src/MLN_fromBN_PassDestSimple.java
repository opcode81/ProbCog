import java.io.File;
import java.io.PrintStream;
import java.util.Random;

import edu.tum.cs.bayesnets.conversion.BN2SRLDB;
import edu.tum.cs.bayesnets.core.*;
import edu.tum.cs.srldb.*;
import edu.tum.cs.srldb.Object;


public class MLN_fromBN_PassDestSimple {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			// open Bayesian network
			BeliefNetworkEx bn = new BeliefNetworkEx("bn/pass_destination_simple.xml");
			
			BN2SRLDB bn2db = new BN2SRLDB(bn);
			Database db = bn2db.getDB(200, new Random(223232));
			
			// do some counting (to verify probabilities in the relearned BN)
			int fromMidf_unmarked = 0, fromMidf_unmarked_toAttacker = 0;
			for(Object obj : db.getObjects()) {
				String passOrigin = obj.getAttributeValue("Position");
				String passDest = obj.getAttributeValue("PassDestination");
				boolean unmarked = obj.getBoolean("UnmarkedAttackers");
				if(unmarked && passOrigin.equals("Midfielder")) {
					fromMidf_unmarked++;
					if(passDest.equals("Attacker"))
						fromMidf_unmarked_toAttacker++;
				}
			}
			
			// write mln
			String dir = "mln/bn_pass_dest_simple/";
			db.writeBasicMLN(new PrintStream(new File(dir + "sampled_bn-empty.mln")));
			db.writeMLNDatabase(new PrintStream(new File(dir + "sampled_bn.db")));			
			
			bn2db.relearnBN();
			bn.saveXMLBIF(dir + "sampled_bn.bif.xml");
			bn.printFullJoint();
			bn.show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
