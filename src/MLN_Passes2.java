import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import edu.tum.cs.srldb.*;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;
import edu.tum.cs.srldb.fipm.*;

/**
 * MLN with passes (actions/situations) where the situations are implicit, i.e. their attributes
 * are converted to action attributes by appending either "before" or "after".
 * For example, an attribute attr with predicate attr(situation, value) is converted
 * to beforeAttr(action, value) and afterAttr(action, value) to convey the state transition  
 * 
 * @author Dominik Jain
 *
 */
public class MLN_Passes2 {
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
						Player toPlayer = (Player)players.get(nGame*22+rsAction.getInt("player"));
						Pass pass = new Pass(database, rsAction, fromPlayer, toPlayer);					
						Situation sitBefore = new Situation(connGame, game, rsAction.getInt("tstart"), motionReader, fromPlayer);
						pass.addSituationAttributes(sitBefore, "before");
						Situation sitAfter = new Situation(connGame, game, rsAction.getInt("tend"), motionReader);
						pass.addSituationAttributes(sitAfter, "after");
						pass.commit();
						
						new Link(database, "beforePossession", pass, fromPlayer).commit();
						new Link(database, "afterPossession", pass, sitAfter.getLink("possession").getArguments()[1]).commit();
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
			// (situations are not added to the data dictionary because we add situations' attributes to actions)
			// - action
			DDObject ddAction = new DDObject("BallAction");
			ddAction.addAttribute(new DDAttribute("successful", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("pass", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("safePass", BooleanDomain.getInstance()));
			ddAction.addAttribute(new DDAttribute("direction", new AutomaticDomain("dir")));
			ddAction.addAttribute(new DDAttribute("length", data.domainLowHigh));
			ddAction.addAttribute(new DDAttribute("speed", data.domainLowHigh));
			addAttributes(ddAction, ddSituation, "before");
			addAttributes(ddAction, ddSituation, "after");
			data.datadict.addObject(ddAction);
			// - relations
			data.datadict.addRelation(new DDRelation("beforePossession", ddAction, data.ddPlayer, false, true));
			data.datadict.addRelation(new DDRelation("afterPossession", ddAction, data.ddPlayer, false, true));
			data.datadict.addRelation(new DDRelation("passFrom", ddAction, data.ddPlayer, false, true));
			data.datadict.addRelation(new DDRelation("passTo", ddAction, data.ddPlayer, false, true));
			
			data.fetch("db='fipm02'");
						
			data.doClustering();
			
			data.database.check();
			
			System.out.print("Writing MLN...");
			//data.database.outputMLNDatabase(new PrintStream(new File("test.db")));
			//data.database.outputMLNBasis(new PrintStream(new File("test.mln")));
			data.database.outputMLNDatabase(new PrintStream(new File("mln/passes2-implicit_sits/" + (allData ? "fipm_full.db" : "fipm.db"))));
			data.database.outputBasicMLN(new PrintStream(new File("mln/passes2-implicit_sits/actsit-empty.mln")));
			System.out.println(" done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	static void addAttributes(DDObject objDest, DDObject objSrc, String prefix) throws DDException {
		for(DDAttribute attr : objSrc.getAttributes().values()) {
			DDAttribute newAttr = attr.clone();
			newAttr.setName(prefix + Database.upperCaseString(attr.getName()));
			objDest.addAttribute(newAttr);
		}
	}
}

