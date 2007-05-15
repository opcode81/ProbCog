import java.io.*;
import edu.tum.cs.srldb.fipm.FIPMData;

public class MLN_Players {
	
	public static void main(String[] args) {
		
		try {
			FIPMData data = new FIPMData();
			data.fetch("good_formations=1");
			//data.fetch("db='fipm02'");
			data.doClustering();
			
			// output MLN	
			System.out.println("writing MLN database...");
			/*Iterator<RelObject> iter = data.players.iterator();							
			while(iter.hasNext()) {
				iter.next().print();
			}*/		
			PrintStream out = new PrintStream(new File("mln/players_teams_games/fipm.db"));
			data.database.outputMLNDatabase(out);
			data.database.outputBasicMLN(new PrintStream(new File("mln/players_teams_games/fipm-empty.mln")));
			
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
}
