import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import de.tum.in.fipm.base.util.Settings;

import edu.tum.cs.srldb.fipm.FIPMData;
import edu.tum.cs.srldb.fipm.Game;
import edu.tum.cs.srldb.fipm.Team;


public class CheckFormations {

	public static void main(String[] args) {
		try {
			// load MySQL driver 
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			
			// add the field good_formations to table game_databases (or reset if already found)
			Settings settings = Settings.getStd();
			String connectString = "jdbc:mysql://" + settings.get("primary_host") + ":" + settings.get("primary_port") + "/fipmmain?user=" + settings.get("primary_user") + "&password=" + settings.get("primary_password");
			final Connection conn = DriverManager.getConnection(connectString);
			Statement stmt = conn.createStatement();
			try {
				stmt.execute("alter table game_databases add good_formations tinyint default 0");
			}
			catch(SQLException e) {
				stmt.execute("update game_databases set good_formations=0");
			}

			// read fipm data and set good_formations to 1 if the formations of both teams aren't chaotic
			FIPMData data = new FIPMData() {
				protected void gameProc(Connection connGame, int nGame, Game game) throws Exception {
					Team t1 = (Team)this.teams.get(this.teams.size()-1);
					Team t2 = (Team)this.teams.get(this.teams.size()-2);
					Statement stmt = conn.createStatement();
					if(!t1.getString("formation").equals("chaotic") && !t2.getString("formation").equals("chaotic")) {						
						stmt.execute("update game_databases set good_formations=1 where db='" + game.getData().getDatabase() + "'");
						System.out.println("  -> good formations");
					}
				}
			};
			data.fetch("1");		
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}

}
