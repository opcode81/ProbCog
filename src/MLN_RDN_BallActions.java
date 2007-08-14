import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Vector;
import java.util.Map.Entry;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.learning.CPTLearner;
import edu.tum.cs.bayesnets.learning.DomainLearner;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary;
import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;
import edu.tum.cs.srldb.fipm.*;

/**
 * Models for ball actions (passes, dribbles, shots)
 *    
 * @author Dominik Jain
 */
public class MLN_RDN_BallActions {
	enum Mode {MLN, RDN, BN, reducedMLN}
	enum SituationModelling {SitNumeric, SitImplicit} 
	
	public static class Counters {
		protected HashMap<String, Integer> counters;
		public Counters() {
			counters = new HashMap<String, Integer>();
		}
		public void inc(String name) {
			Integer c = counters.get(name);
			if(c == null)
				counters.put(name, new Integer(1));
			else
				counters.put(name, new Integer(c+1));			
		}
		public String toString() {
			return counters.toString();
		}
	}
	
	public static void main(String[] args) {
		try {			
			Mode mode = Mode.valueOf(args[0]);		
			SituationModelling sitMode = args.length > 1 ? SituationModelling.valueOf(args[1]) : SituationModelling.SitNumeric;
			final boolean doMLN = mode == Mode.MLN || mode == Mode.reducedMLN;
			final boolean doRDN = mode == Mode.RDN;
			final boolean doBN = mode == Mode.BN; 
			final boolean implicitSits = sitMode == SituationModelling.SitImplicit; // situations implicit rather than numeric constants
			final boolean doReducedDB = mode == Mode.reducedMLN;
			final boolean allData = true || doRDN;
			/**
			 * for reduced MLN only: whether to create a separate player instance for every action in the database (rather than use only 11 player instances per team),
			 * this was added for testing purposes only 
			 */
			final boolean doReducedDBMultiplePlayers = true;  
			
			// counters (just for debugging)
			final Counters cnt = new Counters();
			
			System.out.println("BallActions - " + mode);
			
			BeliefNetworkEx bn = null;
			final Vector<HashMap<String,String>> bnData = new Vector<HashMap<String,String>>();
			if(doBN) {				
				bn = new BeliefNetworkEx(new BeliefNetwork());
				bn.addNode("ballPosX");
				bn.addNode("position");
				bn.addNode("direction");
				bn.addNode("position2");
				//bn.connect("ballPosX", "position2");
				bn.connect("position", "position2");
				bn.connect("position", "direction");
				bn.connect("direction", "position2");	
				bn.connect("ballPosX", "position");
			}
			
			DataDictionary reducedDD = new DataDictionary();
			final Database reducedDB = new Database(reducedDD);

			FIPMData data = new FIPMData() {				
				protected void gameProc(Connection connGame, int nGame, Game game) throws Exception {
					// queries
					String action_query =
						"select\n" +
						"	tstart, tend, player,\n" +
						"	if(successful = 0, 'false', 'true') as successful,\n";
					String pass_query = action_query + 	
						"   second_player,\n" + 
						"	case\n" +
						"		when detail_length = 0 then 'low'\n" +
						"		when detail_length = 1 then 'medium'\n" +
						"		else 'high'\n" +
						"	end as length,\n" +
						"	case\n" +
						"		when detail_velocity = 0 then 'low'\n" +
						"		when detail_velocity = 1 then 'medium'\n" +
						"		else 'high'\n" +
						"	end as speed,\n" +
						"	case\n" +
						"		when detail_direction = 0 then 'back'\n" +
						"		when detail_direction = 1 then 'cross'\n" +
						"		else 'forward'\n" +
						"	end as direction,\n" +
						"	if(detail_savety = 0, 'false', 'true') as safe,\n" +
						"	if(detail_rating = 1, '-', '+') as rating\n" +						
						"from \n" +
						"	episodes join episodes_pass using(id)\n";										
					String shot_query = action_query + 
						"	case\n" +
						"		when detail_velocity=0 then 'low'\n" +
						"		when detail_velocity=1 then 'medium'\n" +
						"		else 'high'\n" +
						"	end as speed,\n" +
						"	if(detail_angle=0, 'obtuse', 'acute') as angle\n" +
						"from episodes join episodes_shot using(id)\n"; 
					String dribble_query = action_query +
						"	case\n" +
						"		when detail_length = 0 then 'low'\n" +
						"		when detail_length = 1 then 'medium'\n" +
						"		else 'high'\n" +
						"	end as length,\n" +
						"	case\n" +
						"		when detail_velocity = 0 then 'low'\n" +
						"		when detail_velocity = 1 then 'medium'\n" +
						"		else 'high'\n" +
						"	end as speed,\n" +
						"	case\n" +
						"		when detail_direction = 0 then 'back'\n" +
						"		when detail_direction = 1 then 'cross'\n" +
						"		else 'forward'\n" +
						"	end as direction,\n" +
						"	if(detail_play_out = 0, 'false', 'true') as outplayedOpponents,\n" +
						"	if(detail_rating = 1, '-', '+') as rating\n" +
						"from episodes join episodes_dribble using(id)\n";				
					// get ordered list of ball actions
					Statement stmtGame = connGame.createStatement();
					ResultSet rsActionList = stmtGame.executeQuery("select id, type, tstart from episodes order by tstart asc, type_ws desc" + (allData ? "" : " limit 100"));
					int nAction = 0, nSituation = 0; 
					int prevEndTime = -1; // time the previous action ended
					Situation prevEndSit = null;
					
					while(rsActionList.next()) {
						
						// do not allow overlapping actions
						if(rsActionList.getInt("tstart") < prevEndTime)
							continue;		
						
						// identify type of ball action and select all the relevant data
						String query;						
						switch(rsActionList.getInt("type")) {
						case 1: // pass
							query = pass_query;
							break;
						case 2: // shot
							query = shot_query;
							break;
						case 3: // dribble					
							query = dribble_query;
							break;
						default:
							throw new Exception("unknown type of episode");
						}
						Statement stmtGame2 = connGame.createStatement();
						ResultSet rsAction = stmtGame2.executeQuery(query + " where id=" + rsActionList.getString("id"));
						rsAction.next();
						
						// instantiate an action object and add it to the database
						BallAction action;
						Player player = (Player)players.get(nGame*22+rsAction.getInt("player"));
						Player playerInPossAfter = player;
						switch(rsActionList.getInt("type")) {
						case 1: // pass
							playerInPossAfter = (Player)players.get(nGame*22+rsAction.getInt("second_player"));
							action = new Pass(database, rsAction, player, playerInPossAfter);
							break;
						case 2: // shot
							action = new Shot(database, rsAction);
							break;
						case 3: // dribble
						default:
							action = new Dribble(database, rsAction);							
							break;
						}									
						action.commit();					
						System.out.println("  action #" + (++nAction) + " (" + action.getClass().getSimpleName() + ": " + rsAction.getString("tstart") + "->" + rsAction.getString("tend") + ")");
						
						// get the situations before and after the action
						Situation sitBefore;
						if(prevEndTime == rsAction.getInt("tstart")) { // see if we can reuse the end situation of the previous action
							sitBefore = prevEndSit;
							sitBefore.setPossession(player); // reset just in case (because now we know for sure who's in possession) 
						}
						else {
							sitBefore = new NumberedSituation(connGame, game, rsAction.getInt("tstart"), motionReader, ++nSituation, player);
							if(!implicitSits)
								sitBefore.commit();
						}
						Situation sitAfter = new NumberedSituation(connGame, game, rsAction.getInt("tend"), motionReader, ++nSituation);
						if(!implicitSits)
							sitAfter.commit();

						// if situations are implicit, add all their attributes to the action
						if(implicitSits) {
							action.addSituationAttributes(sitBefore, "before");
							action.addSituationAttributes(sitAfter, "after");
						}
						
						System.out.println("    successful: " + action.getString("successful"));
						System.out.println("    possession: " + sitBefore.getLink("possession").getArguments()[1] + " -> " + sitAfter.getLink("possession").getArguments()[1]);
						if(action instanceof Pass) {
							if(action.getBoolean("successful")) {
								System.out.println("    " + sitBefore.getAttributeValue("ballPosX") + "," + action.getString("direction") + "," + player.getString("position") + "->" + playerInPossAfter.getString("position"));
							}
						}
						
						Link lnkDoneBy, lnkDoneIn = null;
						(lnkDoneBy = new Link(database, "doneBy", new Object[]{action, player})).commit();
						if(!implicitSits)
							(lnkDoneIn = new Link(database, "doneIn", new Object[]{action, sitBefore})).commit();
						if(doRDN) { // (see below - data dictionary - for details)
							new Link(database, "leadsTo", action, sitAfter).commit();
						}
						
						if(doBN) {
							if(rsActionList.getInt("type") == 1 && action.getBoolean("successful")) { // succ. pass
								HashMap<String,String> data = new HashMap<String,String>();
								data.put("position", player.getString("position"));
								data.put("ballPosX", sitBefore.getString("ballPosX"));
								data.put("direction", action.getString("direction"));
								data.put("position2", playerInPossAfter.getString("position"));
								bnData.add(data);
							}							
						}
						if(doReducedDB) { // reduced MLN: successful passes only
							if(rsActionList.getInt("type") == 1 && action.getBoolean("successful")) { // succ. pass								
								if(!doReducedDBMultiplePlayers) {
									lnkDoneBy.addTo(reducedDB);
									action.addTo(reducedDB);
								}
								else {
									Object playercopy = new Object(reducedDB, player.objType());
									playercopy.addAttributes(player.getAttributes());
									playercopy.commit();
									Object player2copy = new Object(reducedDB, player.objType());
									player2copy.addAttributes(playerInPossAfter.getAttributes());
									player2copy.commit();
									new Link(reducedDB, "doneBy", action, playercopy).commit();
									//action.link("passFrom", playercopy);
									action.link("passTo", player2copy);
									action.addTo(reducedDB);
								}
								if(!implicitSits) {
									reducedDB.addObject(sitBefore);
									//reducedDB.addObject(sitAfter);									
									lnkDoneIn.addTo(reducedDB);										
								}
								
								// counters
								if(sitBefore.getString("ballPosX").equals("offMidfield") && action.getString("direction").equals("back")) {
									cnt.inc("total");
									cnt.inc("P1_" + player.getString("position"));
									cnt.inc("P2_" + playerInPossAfter.getString("position"));
								}
							}
						}

						prevEndTime = rsAction.getInt("tend");
						prevEndSit = sitAfter;
					}
				}
			};
			
			// extend the data dictionary
			// - situation
			DDObject ddSituation = new DDObject("Situation");
			//ddSituation.addAttribute(new DDAttribute("situation", BooleanDomain.getInstance()));
			ddSituation.addAttribute(new DDAttribute("ballPosX", new AutomaticDomain("ballposx")));
			ddSituation.addAttribute(new DDAttribute("ballPosY", new AutomaticDomain("ballposy")));
			AutomaticDomain presenceDomain = new AutomaticDomain("presence");
			ddSituation.addAttribute(new DDAttribute("crossPresence", presenceDomain));
			ddSituation.addAttribute(new DDAttribute("backPresence", presenceDomain));
			ddSituation.addAttribute(new DDAttribute("forwardPresence", presenceDomain));
			ddSituation.addAttribute(new DDAttribute("unmarkedBackMates", BooleanDomain.getInstance()));
			ddSituation.addAttribute(new DDAttribute("unmarkedForwardMates", BooleanDomain.getInstance()));
			ddSituation.addAttribute(new DDAttribute("unmarkedCrossMates", BooleanDomain.getInstance()));
			if(!implicitSits)
				data.datadict.addObject(ddSituation);
			// - action
			DDObject ddAction = new DDObject("BallAction");
			if(implicitSits) {
				addAttributes(ddAction, ddSituation, "before");
				addAttributes(ddAction, ddSituation, "after");
			}
			ddAction.addAttribute(new DDAttribute("successful", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("speed", data.domainLowHigh));
			// -- pass
			ddAction.addAttribute(new DDAttribute("pass", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("safePass", BooleanDomain.getInstance()));
			// -- pass, dribble
			ddAction.addAttribute(new DDAttribute("direction", new AutomaticDomain("dir")));
			ddAction.addAttribute(new DDAttribute("length", data.domainLowHigh));			
			// -- dribble
			ddAction.addAttribute(new DDAttribute("dribble", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("outplayedOpponents", BooleanDomain.getInstance()));
			// -- shot
			ddAction.addAttribute(new DDAttribute("shot", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("angle", new AutomaticDomain("shotAngle")));
			data.datadict.addObject(ddAction);
			// - relations
			DDRelation relDoneBy, relDoneIn;
			data.datadict.addRelation(relDoneBy = new DDRelation("doneBy", ddAction, data.ddPlayer, false, true));
			data.datadict.addRelation(relDoneIn = new DDRelation("doneIn", ddAction, ddSituation, true, false));			
			//if(doRDN) data.datadict.addRelation(new DDRelation("startSit", ddAction, ddSituation));
			if(doRDN) data.datadict.addRelation(new DDRelation("leadsTo", ddAction, ddSituation));
			if(!implicitSits) data.datadict.addRelation(new DDRelation("possession", ddSituation, data.ddPlayer, false, true));
			DDRelation relPassFrom, relPassTo;
			data.datadict.addRelation(relPassFrom = new DDRelation("passFrom", ddAction, data.ddPlayer, false, true));
			data.datadict.addRelation(relPassTo = new DDRelation("passTo", ddAction, data.ddPlayer, false, true));
			
			data.fetch("db='fipm02'");
			
			data.doClustering();
			
			data.database.check();
			
			if(doMLN) {
				if(!doReducedDB) {
					System.out.print("Writing MLN...");
					//data.database.outputMLNDatabase(new PrintStream(new File("test.db")));
					//data.database.outputMLNBasis(new PrintStream(new File("test.mln")));
					data.database.outputMLNDatabase(new PrintStream(new File("mln/ball_actions/" + "fipm" + (allData ? "_full" : "") + (implicitSits ? "-implsits" : "") + ".db")));
					data.database.outputBasicMLN(new PrintStream(new File("mln/ball_actions/actsit-empty" + (implicitSits ? "-implsits" : "") + ".mln")));
					data.database.getDataDictionary().outputAttributeList(new PrintStream(new File("mln/ball_actions/attributes" + (implicitSits ? "-implsits" : "") + ".txt")));
					//data.datadict.outputAttributeLists(new PrintStream(new File("mln/ball_actions/attributes_all" + (implicitSits ? "-implsits" : "") + ".txt")));
				}
				else { // use reduced database
					// add stuff to the data dictionary
					data.ddPlayer.discardAllAttributesExcept(new String[]{"position"});
					reducedDD.addObject(data.ddPlayer);
					ddAction.discardAllAttributesExcept(new String[]{"beforeBallPosX", "direction"});
					reducedDD.addObject(ddAction);
					if(!implicitSits) {
						ddSituation.discardAllAttributesExcept(new String[]{"ballPosX"});
						reducedDD.addObject(ddSituation);
					}					
					reducedDD.addRelation(relDoneBy);
					if(!implicitSits) reducedDD.addRelation(relDoneIn);
					reducedDD.addRelation(relPassTo);
					reducedDD.addRelation(relPassFrom);
					
					// add players			
					if(!doReducedDBMultiplePlayers)
						for(Object p : data.players) 
							reducedDB.addObject(p);
					
					reducedDB.check();
					
					reducedDB.outputMLNDatabase(new PrintStream(new File("mln/ball_actions-reduced/" + "fipm" + (allData ? "_full" : "") + "-passes" + (implicitSits ? "-implsits" : "") + (doReducedDBMultiplePlayers ? "-multplayers" : "") + ".db")));
					reducedDB.outputBasicMLN(new PrintStream(new File("mln/ball_actions-reduced/actsit-passes-empty" + (implicitSits ? "-implsits" : "") + (doReducedDBMultiplePlayers ? "-multplayers" : "") + ".mln")));
					
					System.out.println("\nCounters:\n" + cnt.toString());
				}
			}
			if(doRDN) {
				System.out.println("Writing Proximity database");
				data.database.outputProximityDatabase(new PrintStream(new File("proximity/ball_actions/fipm.proxdb.xml")));
				data.database.outputBasicMLN(new PrintStream(new File("proximity/ball_actions/fipm.mln")));
				data.database.getDataDictionary().outputAttributeLists(new PrintStream(new File("proximity/ball_actions/attributes.txt")));
			}
			if(doBN) {
				DomainLearner dl = new DomainLearner(bn.bn);
				for(HashMap<String,String> ex : bnData)
					dl.learn(ex);				
				dl.finish();
				CPTLearner cptl = new CPTLearner(dl);
				for(HashMap<String,String> ex : bnData)
					cptl.learn(ex);
				cptl.finish();
				bn.saveXMLBIF("mln/bn_pass_dest/bn_pass.bif.xml");
				bn.show();
			}
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	static void addAttributes(DDObject objDest, DDObject objSrc, String prefix) throws DDException {
		for(DDAttribute attr : objSrc.getAttributes().values()) {
			DDAttribute newAttr = attr.clone();
			newAttr.setName(prefix + Database.upperCaseString(attr.getName()));
			newAttr.setOwner(null);
			objDest.addAttribute(newAttr);
		}
	}
}
