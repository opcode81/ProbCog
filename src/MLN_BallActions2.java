import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.*;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.domain.*;
import edu.tum.cs.srldb.fipm.*;

/**
 * MLN with all ball actions (passes, dribbles, shots) and
 * separate databases for each action
 * 
 * @author Dominik Jain
 *
 */
public class MLN_BallActions2 {
	public static void main(String[] args) {
		try {
			final boolean allData = true;
			final Vector<Database> databases = new Vector<Database>();
			
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
					ResultSet rsActionList = stmtGame.executeQuery("select id, type, tstart from episodes order by tstart" + (allData ? "" : " limit 10"));
					int nAction = 0; 
					int prevEndTime = -1; // time the previous action ended
					
					while(rsActionList.next()) {					
						// do not allow overlapping actions
						if(rsActionList.getInt("tstart") < prevEndTime)
							continue;		
						
						// create a new database for this action
						Database database = new Database(this.datadict);
						game.addTo(database);
						this.teams.lastElement().addTo(database);
						this.teams.elementAt(teams.size()-2);
						for(Object player : this.currentTeamPlayers.elementAt(0))
							player.addTo(database);
						for(Object player : this.currentTeamPlayers.elementAt(1))
							player.addTo(database);						
						
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
							throw new Exception("unknown type of episode: " + rsActionList.getInt("type"));
						}
						Statement stmtGame2 = connGame.createStatement();
						ResultSet rsAction = stmtGame2.executeQuery(query + " where id=" + rsActionList.getString("id"));
						rsAction.next();
						
						// instantiate an object and add it to the database
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
						System.out.print("  action #" + (++nAction) + " (" + action.getClass().getSimpleName() + ": " + rsAction.getString("tstart") + "->" + rsAction.getString("tend") + ")\r");
						
						// get the situations before and after the action
						Situation sitBefore;
						sitBefore = new NumberedSituation(connGame, game, rsAction.getInt("tstart"), motionReader, nAction*2-1, player);
						sitBefore.addTo(database);
						Situation sitAfter = new NumberedSituation(connGame, game, rsAction.getInt("tend"), motionReader, nAction*2);						
						sitAfter.addTo(database);
						
						// link which names the action that is performed
						new Link(database, "do", new Object[]{action, player, sitBefore}).commit();

						databases.add(database);
						
						prevEndTime = rsAction.getInt("tend");
					}
				}
			};
			
			// extend the data dictionary
			// - situation
			DDObject ddSituation = new DDObject("Situation");
			ddSituation.addAttribute(new DDAttribute("situation", BooleanDomain.getInstance()));
			ddSituation.addAttribute(new DDAttribute("ballPosX", new AutomaticDomain("ballposx")));
			ddSituation.addAttribute(new DDAttribute("ballPosY", new AutomaticDomain("ballposy")));
			AutomaticDomain presenceDomain = new AutomaticDomain("presence");
			ddSituation.addAttribute(new DDAttribute("crossPresence", presenceDomain));
			ddSituation.addAttribute(new DDAttribute("backPresence", presenceDomain));
			ddSituation.addAttribute(new DDAttribute("forwardPresence", presenceDomain));
			ddSituation.addAttribute(new DDAttribute("unmarkedBackMates", BooleanDomain.getInstance()));
			ddSituation.addAttribute(new DDAttribute("unmarkedForwardMates", BooleanDomain.getInstance()));
			ddSituation.addAttribute(new DDAttribute("unmarkedCrossMates", BooleanDomain.getInstance()));
			data.datadict.addObject(ddSituation);
			// - action
			DDObject ddAction = new DDObject("BallAction");
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
			ddAction.addAttribute(new DDAttribute("angle", new OrderedStringDomain("shotAngle", new String[]{"obtuse", "acute"})));
			data.datadict.addObject(ddAction);
			// - relations
			data.datadict.addRelation(new DDRelation("do", new DDObject[]{ddAction, data.ddPlayer, ddSituation}/*, new boolean[]{true, true, false}*/)); 
			data.datadict.addRelation(new DDRelation("possession", ddSituation, data.ddPlayer, false, true));
			data.datadict.addRelation(new DDRelation("passFrom", ddAction, data.ddPlayer, false, true));
			data.datadict.addRelation(new DDRelation("passTo", ddAction, data.ddPlayer, false, true));
			
			// fetch data from the FIPM database
			data.fetch("db='fipm02'");						
		
			// apply clustering where necessary
			data.database.doClustering();
			
			System.out.println("Writing example databases...");
			int i = 0;			
			for(Database db : databases) {				
				db.check();				
				db.outputMLNDatabase(new PrintStream(new File("mln/ball_actions2/train/ex" + (i++) + ".db")));
			}
			
			System.out.println("Writing MLN...");
			//data.database.outputMLNBasis(new PrintStream(new File("test.mln")));
			data.database.outputBasicMLN(new PrintStream(new File("mln/ball_actions2/actsit-empty.mln")));
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
