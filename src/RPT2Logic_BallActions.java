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


public class RPT2Logic_BallActions {
	public static void main(String[] args) {
		try {
			// connect to proximity database
			ProxDB db = new ProxDB("localhost:4546");
			
			// file to write formulas to
			PrintStream outFile = new PrintStream(new File("proximity/formulas.txt"));
			
			// process all the RPT files
			String rdnDir = "proximity/ball_actions/succPass";
			File dir = new File(rdnDir);
			String[] files = dir.list();
			Pattern p = Pattern.compile("RPT[^_]*_([^_]+)_([^\\._]+)(?:_(.*))?\\.xml"); // naming convention: RPT*_item_attribute[_other].xml
			for(int i = 0; i < files.length; i++) {			 
				Matcher m = p.matcher(files[i]);
				if(m.matches() /*&& files[i].equals("RPTforRDN_player_backPassShare.xml")*/) {
					String item = m.group(1);
					String attribute = m.group(2);
					String suffix = m.group(3);

					System.out.println(item + "." + attribute);
					outFile.println("// " + attribute);
					
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
					
					if(suffix.equals("succPass"))
						r2l.setPrecondition("pass(a) ^ successful(a)");
					
					// set specifics for each core object, especially relations that apply
					if(item.equals("player")) {						
						ObjectType team = r2l.setObjectType("team", "t", "inTeam(p, t)", null);
						ObjectType game = r2l.setObjectType("game", "g", "playedIn(t, g)", new ObjectType[]{team});
						r2l.setObjectType("otherTeam", "ot", "playedIn(ot, g)", new ObjectType[]{game});
						// specify which items are links
						r2l.setRelationType("playedIn", team, game);
					}
					else if(item.equals("receiver")) {						
						r2l.setCoreItemVar("r");
						ObjectType action = r2l.setObjectType("action", "a", "doneBy(a, p) ^ passTo(a, r)", null);
						ObjectType player = r2l.setObjectType("player", "p", null, new ObjectType[]{action});
						ObjectType sitBefore = r2l.setObjectType("sitBefore", "s", "doneIn(a, s)", new ObjectType[]{action});
						ObjectType team = r2l.setObjectType("team", "t", "inTeam(p, t)", new ObjectType[]{player});
						ObjectType game = r2l.setObjectType("game", "g", "playedIn(t, g)", new ObjectType[]{team});
						r2l.setObjectType("otherTeam", "ot", "playedIn(ot, g)", new ObjectType[]{game});
						r2l.setRelationType("playedIn", team, game);
					}
					else if(item.equals("team")) {
						ObjectType game = r2l.setObjectType("game", "g", "playedIn(t, g)", null);
						ObjectType ot = r2l.setObjectType("otherTeam", "ot", "playedIn(ot, g)", new ObjectType[]{game});
						ObjectType player = r2l.setObjectType("player", "p", "inTeam(p, t)", null);
						r2l.setRelationType("playedIn", r2l.getCoreObject(), game);
					}
					else if(item.equals("playedIn")) {
						ObjectType team = r2l.setObjectType("team", "t", null, null);
						ObjectType game = r2l.setObjectType("game", "g", null, null);
						ObjectType other_team = r2l.setObjectType("otherTeam", "ot", "playedIn(ot, g)", null);
						r2l.setRelationType("playedIn", team, game);
					}
					else if(item.equals("game")) {
						ObjectType team = r2l.setObjectType("team", "t", "playedIn(t, g)", null);
						ObjectType other_team = r2l.setObjectType("otherTeam", "ot", "playedIn(ot, g)", null);
						r2l.setRelationType("playedIn", team, r2l.getCoreObject());
					}
					else if(item.equals("action")) {
						ObjectType player = r2l.setObjectType("player", "p", "doneBy(a, p)", null);
						ObjectType sitBefore = r2l.setObjectType("sitBefore", "s", "doneIn(a, s)", null);
						r2l.setObjectType("sitAfter", "succ(t)", null, new ObjectType[]{sitBefore});						
						ObjectType team = r2l.setObjectType("team", "t", "inTeam(p, t)", new ObjectType[]{player});
						ObjectType game = r2l.setObjectType("game", "g", "playedIn(t, g)", new ObjectType[]{team});
						r2l.setRelationType("playedIn", team, game);
					}
					else if(item.equals("sitAfter")) {
						r2l.setCoreItemVar("succ(s)");
						ObjectType sitBefore = r2l.setObjectType("sitBefore", "s", "doneIn(a, s)", null);
						ObjectType action  = r2l.setObjectType("action", "a", null, new ObjectType[]{sitBefore});
						ObjectType player = r2l.setObjectType("player", "p", "doneBy(a, p)", new ObjectType[]{action});						
					}
					
					// walk the tree to read all paths
					r2l.walkTree(root);
					
					// retrieve formulas
					for(String formula : r2l.getFormulas(true, false)) {
						if(true) {
							formula = formula.replace("doneIn(a, s)", "");
							formula = Pattern.compile("(\\w+)\\(succ\\(s\\)(.*?)\\)").matcher(formula).replaceAll("after$1(a$2)");
							formula = Pattern.compile("(\\w+)\\(s(.*?)\\)").matcher(formula).replaceAll("before$1(a$2)");
							StringBuffer s = new StringBuffer(formula);
							for(int idx = 0;; idx++) {
								idx = s.indexOf("before", idx);
								if(idx == -1)
									break;
								s.setCharAt(idx+6, Character.toUpperCase(s.charAt(idx+6)));
							}
							for(int idx = 0;;) {
								idx = s.indexOf("after", idx);
								if(idx == -1)
									break;
								s.setCharAt(idx+5, Character.toUpperCase(s.charAt(idx+5)));
							}
							formula = s.toString();
						}		
						formula = Pattern.compile("\\^\\s+(\\^|\\=\\>)").matcher(formula).replaceAll("$1");
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
