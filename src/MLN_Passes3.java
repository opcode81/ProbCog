import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import edu.tum.cs.srldb.*;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;
import edu.tum.cs.srldb.fipm.*;

/**
 * MLN with passes (actions/situations) where the situations are integers instead of regular named constants,
 * as that allows the Markovian assumption to be imposed by requiring that each formula contain only
 * t and succ(t)
 * 
 * @author Dominik Jain
 *
 */
public class MLN_Passes3 {
	public static void main(String[] args) {
		try {
			final boolean allData = true;
			
			FIPMData data = new FIPMData() {				
				protected void gameProc(Connection connGame, int nGame, Game game) throws Exception {
					// passes
					String query = 
						"select\n" +
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
						"	if(detail_rating = 1, '-', '+') as rating,\n" +
						"	if(successful = 0, 'false', 'true') as successful,\n" +
						"	tstart, tend, player, second_player\n" +
						"from \n" +
						"	episodes join episodes_pass using(id)\n" + 
						(allData ? "" : " limit 50");				
					Statement stmtGame = connGame.createStatement();
					ResultSet rsAction = stmtGame.executeQuery(query);
					int nPass = 0;
					while(rsAction.next()) {
						System.out.print("  pass #" + ++nPass + "\r");
						Player fromPlayer = (Player)players.get(nGame*22+rsAction.getInt("player"));
						Player toPlayer = (Player)players.get(nGame*22+rsAction.getInt("second_player"));
						Pass pass = new Pass(database, rsAction, fromPlayer, toPlayer);
						pass.commit();					
						
						Situation sitBefore = new NumberedSituation(connGame, game, rsAction.getInt("tstart"), motionReader, nPass*2-1, fromPlayer);
						sitBefore.commit();
						Situation sitAfter = new NumberedSituation(connGame, game, rsAction.getInt("tend"), motionReader, nPass*2);
						sitAfter.commit();
						
						new Link(database, "do", pass, sitBefore).commit();
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
			ddAction.addAttribute(new DDAttribute("pass", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("safePass", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("direction", new AutomaticDomain("dir")));
			ddAction.addAttribute(new DDAttribute("length", data.domainLowHigh));
			ddAction.addAttribute(new DDAttribute("speed", data.domainLowHigh));
			data.datadict.addObject(ddAction);
			// - relations
			data.datadict.addRelation(new DDRelation("do", ddAction, ddSituation/*, true, false*/)); 
			data.datadict.addRelation(new DDRelation("possession", ddSituation, data.ddPlayer, false, true));
			data.datadict.addRelation(new DDRelation("passFrom", ddAction, data.ddPlayer, false, true));
			data.datadict.addRelation(new DDRelation("passTo", ddAction, data.ddPlayer, false, true));
			
			data.fetch("db='fipm02'");
						
			data.doClustering();
			
			data.database.check();
			
			System.out.print("Writing MLN...");
			//data.database.outputMLNDatabase(new PrintStream(new File("test.db")));
			//data.database.outputMLNBasis(new PrintStream(new File("test.mln")));
			data.database.outputMLNDatabase(new PrintStream(new File("mln/passes3-numeric/" + (allData ? "fipm_full.db" : "fipm.db"))));
			data.database.outputBasicMLN(new PrintStream(new File("mln/passes3-numeric/actsit-empty.mln")));
			System.out.println(" done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
