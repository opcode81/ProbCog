import java.io.File;
import java.io.PrintStream;
import java.util.Random;
import java.util.HashMap;

import edu.tum.cs.bayesnets.conversion.BN2SRLDB;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.AutomaticDataDictionary;

public class MLN_fromBN_PassDest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			// open Bayesian network
			BeliefNetworkEx bn = new BeliefNetworkEx("mln/bn_pass_dest/bn_pass.bif.xml");
			
			BN2SRLDB bn2db = new BN2SRLDB(bn);
			Database db = bn2db.getDB(100, new Random(120121));
			
			/**
			 * for relational DB:
			 * whether to have only one instance of each type of player rather than add a separate
			 * player for each action
			 */
			final boolean relDBSinglePlayers = true; 
			
			// rewrite the db with relations
			Database relDB = new Database(new AutomaticDataDictionary());
			HashMap<String, Object> singlePlayers = null;
			if(relDBSinglePlayers) {
				String[] positions = new String[]{"goalkeeper", "defender", "midfielder", "attacker"};
				singlePlayers = new HashMap<String, Object>();
				for(int i = 0; i < positions.length; i++) {
					String pos = positions[i];
					Object player = new Object(relDB, "Player");
					player.addAttribute("position", pos);
					player.commit();
					singlePlayers.put(pos, player);
				}
			}
			for(Object obj : db.getObjects()) {
				Object player = null, player2 = null;
				if(!relDBSinglePlayers) {
					player = new Object(relDB, "Player");
					player.addAttribute("position", obj.getString("position"));
					player.commit();
					player2 = new Object(relDB, "Player");
					player2.addAttribute("position", obj.getString("position2"));
					player2.commit();
				}
				else {
					player = singlePlayers.get(obj.getString("position"));
					player2 = singlePlayers.get(obj.getString("position2"));
				}
				Object pass = new Object(relDB, "Pass");
				pass.addAttribute("direction", obj.getString("direction"));
				pass.commit();
				Object sit = new Object(relDB, "Situation");
				sit.addAttribute("ballPosX", obj.getString("ballPosX"));
				sit.commit();
				Link doneBy = new Link(relDB, "doneBy", pass, player);
				doneBy.commit();
				Link passTo = new Link(relDB, "passTo", pass, player2);
				passTo.commit();
				Link doneIn = new Link(relDB, "doneIn", pass, sit);
				doneIn.commit();
			}
			relDB.check();
			
			// write mlns and dbs
			String dir = "mln/bn_pass_dest/";
			PrintStream outMLN = new PrintStream(new File(dir + "sampled_bn.mln"));
			db.outputBasicMLN(outMLN);
			bn2db.writeMLNFormulas(outMLN);
			db.outputMLNDatabase(new PrintStream(new File(dir + "sampled_bn.db")));			
			relDB.outputMLNDatabase(new PrintStream(new File(dir + "sampled_bn-relations" + (relDBSinglePlayers ? "-singleplayers" : "") + ".db")));
			relDB.outputBasicMLN(new PrintStream(new File(dir + "sampled_bn-relations-empty.mln")));
			
			bn2db.relearnBN();
			bn.saveXMLBIF(dir + "sampled_bn.bif.xml");
			bn.show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
