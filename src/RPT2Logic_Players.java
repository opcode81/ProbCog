import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kdl.prox3.db.ProxDB;
import kdl.prox3.model.classifiers.RPNode;
import kdl.prox3.model.classifiers.RPT;
import edu.tum.cs.rpt.RPT2Logic;
import edu.tum.cs.rpt.RPT2Logic.ObjectType;


public class RPT2Logic_Players {
	public static void main(String[] args) {
		try {
			// connect to proximity database
			ProxDB db = new ProxDB("localhost:4545");
			
			// file to write formulas to
			PrintStream outFile = new PrintStream(new File("proximity/formulas.txt"));
			
			// process all the RPT files
			String rdnDir = "proximity/players_teams_games/rdn_reduced3";
			File dir = new File(rdnDir);
			String[] files = dir.list();
			Pattern p = Pattern.compile("RPT[^_]*_([^_]+)_([^\\._]+)(?:_.*)?\\.xml"); // naming convention: RPT*_item_attribute[_other].xml
			for(int i = 0; i < files.length; i++) {			 
				Matcher m = p.matcher(files[i]);
				if(m.matches() /*&& files[i].equals("RPTforRDN_player_backPassShare.xml")*/) {
					String item = m.group(1);
					String attribute = m.group(2);

					System.out.println(item + "." + attribute);
					
					// read the relational probability tree and get the root node
					RPT rpt = new RPT(attribute, db);
					rpt.readFromXML(rdnDir + "/" + files[i]);
					RPNode root = rpt.getRoot();
					
					// create the converter for this RPT
					RPT2Logic r2l;
					// if the item is a link, use the appropriate constructor					
					if(item.equals("playedIn")) 
						r2l = new RPT2Logic(attribute, item);
					else
						r2l = new RPT2Logic(attribute, item, item.substring(0,1));					
					
					// set specifics for each core object, especially relations that apply
					if(item.equals("player")) {
						ObjectType team = r2l.setObjectType("team", "t", "inTeam(p, t)", null);
						ObjectType game = r2l.setObjectType("game", "g", "playedIn(t, g)", new ObjectType[]{team});
						r2l.setObjectType("other_team", "ot", "playedIn(ot, g)", new ObjectType[]{game});
						// specify which items are links
						r2l.setRelationType("playedIn", team, game);
					}
					else if(item.equals("team")) {
						ObjectType game = r2l.setObjectType("game", "g", "playedIn(t, g)", null);
						ObjectType ot = r2l.setObjectType("other_team", "ot", "playedIn(ot, g)", new ObjectType[]{game});
						ObjectType player = r2l.setObjectType("player", "p", "inTeam(p, t)", null);
						r2l.setRelationType("playedIn", r2l.getCoreObject(), game);
					}
					else if(item.equals("playedIn")) {
						ObjectType team = r2l.setObjectType("team", "t", null, null);
						ObjectType game = r2l.setObjectType("game", "g", null, null);
						ObjectType other_team = r2l.setObjectType("other_team", "ot", "playedIn(ot, g)", null);
						r2l.setRelationType("playedIn", team, game);
					}
					else if(item.equals("game")) {
						ObjectType team = r2l.setObjectType("team", "t", "playedIn(t, g)", null);
						ObjectType other_team = r2l.setObjectType("other_team", "ot", "playedIn(ot, g)", null);
						r2l.setRelationType("playedIn", team, r2l.getCoreObject());
					}
					
					// walk the tree to read all paths
					r2l.walkTree(root);
					
					// retrieve formulas
					Vector<String> formulas = r2l.getFormulas(true, true);
					Iterator<String> iter = formulas.iterator();
					while(iter.hasNext()) {
						String formula = iter.next();
						//System.out.println("  " + formula);
						outFile.println(formula);						
					}
				}			
			}
		}
		catch(Exception e) {
			System.err.println(e);
			e.printStackTrace();
		}
		System.out.println("done!");
	}

}
