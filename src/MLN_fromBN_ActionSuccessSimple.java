import java.io.File;
import java.io.PrintStream;
import java.util.Random;

import edu.tum.cs.bayesnets.conversion.BN2SRLDB;
import edu.tum.cs.bayesnets.core.*;
import edu.tum.cs.srldb.*;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;


public class MLN_fromBN_ActionSuccessSimple {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			// open Bayesian network
			BeliefNetworkEx bn = new BeliefNetworkEx("bn/action_success_simple.xml");
			
			BN2SRLDB bn2db = new BN2SRLDB(bn);
			bn2db.setBooleanConversion("actionType");
			Database db = bn2db.getDB(200, new Random(223232));
			
			DDAttribute attrAction = new DDAttribute("action", BooleanDomain.getInstance());			
			db.getDataDictionary().getObjects().iterator().next().addAttribute(attrAction);
			db.getDataDictionary().addAttribute(attrAction);
			int passes = 0, shots = 0, samples = 0, successful = 0, successful_passes = 0, successful_shots = 0;
			int notPassOrSucc = 0, notShotOrSucc = 0, notShotOrNotSucc = 0;
			for(Object obj : db.getObjects()) {
				obj.addAttribute("action", "true");
				samples++;
				boolean bPass = obj.getString("actionType").equals("pass"); 
				boolean bShot = !bPass;
				boolean bSuccessful = obj.getString("successful").equals("True");
				if(bPass) {
					passes++;
					if(bSuccessful) {
						successful++;
						successful_passes++;
					}
				}
				else {
					shots++;
					if(bSuccessful) {
						successful++;
						successful_shots++;
					}
				}
				if(!bPass || bSuccessful)
					notPassOrSucc++;
				if(!bShot || bSuccessful)
					notShotOrSucc++;
				if(!bShot || !bSuccessful)
					notShotOrNotSucc++;
			}	
			
			// write mln
			String dir = "mln/bn_action_success_simple/";
			db.writeBasicMLN(new PrintStream(new File(dir + "sampled_bn.mln")));
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
