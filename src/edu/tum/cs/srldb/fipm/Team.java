package edu.tum.cs.srldb.fipm;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;

/**
 * contains functions for the analysis of teams in FIPM
 * @author Dominik Jain
 */
public class Team extends Object {
	
	public Vector goalkeepers;
	public PlayerGroup defenders, midfielders, attackers;
	public int teamNo;
	public int scoreDiff;
	
	public Team(Connection conn, Game game, int teamNo) throws Exception {
		super(game.getDatabase());
		this.teamNo = teamNo;
		
		// modify the database query
		String query = 
			"select finPA.PassAccuracy as passAccuracy, \n" +
			"	   finBP.BallPossession as ballPossession, \n" +
			"	   finAttack.AttackEffectiveness as attackEffectiveness,\n" +
			"	   finAttack.Attacks as attacks,\n" +
			"	   finDefEff.DefenseEffectiveness as defenseEffectiveness,\n" +
			"	   finBallHalf.BallInOpponentHalf as ballInOpponentHalf,\n" +
			"	   finRunDist.AvgRunDistance as avgRunDistance,\n" +
			"	   finShot.AvgVelocity as avgShotVelocity,\n" +
			"	   finSideline.sidelineDist as avgSidelineDist\n" +
			"from\n" +
			"    (select \n" +
			"    	pass_percentage as PassAccuracy\n" +
			"     from \n" +
			"    	(select \n" +
			"    		player, \n" +
			"    		sum(success) / count(*) as pass_percentage \n" +
			"    	 from\n" +
			"    		(select \n" +
			"				player, \n" +
			"				(player < 11 and second_player < 11) or (player > 10 and second_player > 10) as success \n" +
			"    		 from episodes, episodes_pass \n" +
			"             where episodes.id = episodes_pass.id\n" +
			"            ) as passes_succ \n" +
			"         group by player\n" +
			"        ) as foo \n" +
			"     where player $player_cond\n" +
			"    ) as finPA,\n" +
			"    \n" +
			"    (select \n" +
			"    	sum((tend-tstart)*(player $player_cond))/sum(tend-tstart) as BallPossession\n" +
			"     from episodes\n" +
			"    ) as finBP,\n" +
			"    \n" +
			"\n" +
			"    (select \n" +
			"    	if(sum(goal)/count(*) is null, 0, sum(goal)/count(*)) as AttackEffectiveness,\n" +
			"    	if(count(*) is null, 0, count(*)) as Attacks\n" +
			"     from attacks \n" +
			"     where team=$team\n" +
			"    ) as finAttack,\n" +
			"    \n" +
			"    (select \n" +
			"    	if((count(*)-sum(goal))/count(*) is null, 1, (count(*)-sum(goal))/count(*)) as DefenseEffectiveness\n" +
			"     from attacks \n" +
			"     where team=$other_team\n" +
			"    ) as finDefEff,\n" +
			"    \n" +
			"    (select \n" +
			"		percentage as BallInOpponentHalf\n" +
			"	 from     	\n" +
			"	    (select 1 as team, @x:=(SUM((x>0 and x_end>0)*(t_end-time)) +\n" +
			"	        SUM((SIGN(x)!=SIGN(x_end))*(t_end-time)*(1+(x+x_end)/ABS(x-x_end))/2)) /\n" +
			"	        SUM(t_end-time) as percentage\n" +
			"		 from motion\n" +
			"		 union\n" +
			"		 select 0 as team, 1-@x as percentage\n" +
			"		) as foo\n" +
			"	 where team=$other_team\n" +
			"	) as finBallHalf,\n" +
			"	\n" +
			"	(select \n" +
			"		avg(STA_distance) as AvgRunDistance\n" +
			"	 from \n" +
			"	 	(SELECT object AS STR_obj, SUM(SQRT((x_end-x)*(x_end-x)+(y_end-y)*(y_end-y))) AS STA_distance\n" +
			"		 FROM motion\n" +
			"		 WHERE vx*vx+vy*vy<2250000 AND object $player_cond\n" +
			"		 GROUP BY object) as foo\n" +
			"	) as finRunDist,\n" +
			"	\n" +
			"	(select if(avg(velocity_start) is null, 0, avg(velocity_start)) as AvgVelocity\n" +
			"	 from episodes join episodes_shot using(id)\n" +
			"	 where player $player_cond\n" +
			"	) as finShot,	\n" +
			"	\n" +
			"	(select \n" +
			"		avg(34000-ABS(y+y_end)/2) as SidelineDist\n" +
			"	 from \n" +
			"	   (select * from motion where object=-1) as m \n" +
			"	   join\n" +
			"       (select * from episodes where player $player_cond) as e \n" +
			"       on (m.time>=e.tstart and m.t_end<=e.tend)\n" +
			"	) as finSideline\n";
		if(teamNo == 0)
			query = query.replaceAll("\\$player_cond", "between 0 and 10").replaceAll("\\$team", "0").replaceAll("\\$other_team", "1");
		else
			query = query.replaceAll("\\$player_cond", "between 11 and 21").replaceAll("\\$team", "1").replaceAll("\\$other_team", "0");
		
		// execute the query
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(query);
		this.addAttribsFromResultSet(rs, true);
		
		// get the team formation
		defenders = new DefenderGroup();
		midfielders = new MidfielderGroup();
		attackers = new AttackerGroup();
		goalkeepers = new Vector();
		addAttribute("formation", getTeamFormation(conn, teamNo));
		
		Link linkToGame = link("playedIn", game);
		
		scoreDiff = game.getData().getHomeScore() - game.getData().getAwayScore();
		if(teamNo == 1) scoreDiff *= -1;
		if(scoreDiff < 0) {
			linkToGame.addAttribute("lose", "true");
			linkToGame.addAttribute("win", "false");
		}
		else {
			linkToGame.addAttribute("win", scoreDiff > 0 ? "true" : "false");
			linkToGame.addAttribute("lose", "false");			
		}
	}
	
	private class PlayerPos implements Comparable {
		public int player, next_player;
		/**
		 * distance to next player
		 */		
		public double dist;
		public double x, y;
		public int index;
		
		public PlayerPos(int player, int next_player, double x, double dist, double y, int index) {
			this.player = player;
			this.next_player = next_player;
			this.dist = dist;
			this.index = index;
			this.x = x;
			this.y = y;
		}
		
		public int compareTo(java.lang.Object other) {
			PlayerPos o = (PlayerPos) other;
			if(this.dist < o.dist)
				return 1;
			if(this.dist > o.dist)
				return -1;
			return 0;
		}
	}
	
	private abstract class PlayerGroup {		
		protected Vector<PlayerPos> players;
		protected double min_x, max_x, min_y, max_y;
		
		public PlayerGroup() {
			players = new Vector<PlayerPos>();
			max_x = max_y = Double.MIN_VALUE;
			min_x = min_y = Double.MAX_VALUE;
		}
		
		public void add(PlayerPos player) {
			players.add(player);
			if(player.x < min_x)
				min_x = player.x;
			if(player.x > max_x)
				max_x = player.x;
			if(player.y < min_y)
				min_y = player.y;
			if(player.y > max_y)
				max_y = player.y;			
		}
		
		public PlayerPos getPos(int playerNo) {
			Iterator<PlayerPos> i = players.iterator();
			while(i.hasNext()) {
				PlayerPos p = i.next();
				if(p.player == playerNo)
					return p;
			}
			return null;
		}
		
		public boolean contains(int playerNo) {
			return getPos(playerNo) != null;
		}
	
		public abstract String getRole(int playerNo);
	}

	private class DefenderGroup extends PlayerGroup {
		public DefenderGroup() {
			super();
		}
		
		public String getRole(int playerNo) {
			PlayerPos p = getPos(playerNo);
			if(Math.abs(p.y) < 6000)
				return "centre back";
			else
				return "side back";
		}
	}

	private class AttackerGroup extends PlayerGroup {
		public AttackerGroup() {
			super();
		}
		
		public String getRole(int playerNo) {
			PlayerPos p = getPos(playerNo);
			if(Math.abs(p.y) < 6000)
				return "striker";
			else
				return "wing";
		}
	}

	private class MidfielderGroup extends PlayerGroup {
		public MidfielderGroup() {
			super();
		}
		
		public String getRole(int playerNo) {
			PlayerPos p = getPos(playerNo);
			if(Math.abs(p.y) < 1500 && p.x == min_x)
				return "centre midfielder";
			if(Math.abs(p.y) < 6000)
				return "inside midfielder";
			else
				return "side midfielder";
		}
	}
	
	/**
	 * returns the formation of the team as a string in the format "D-M-A" (where D is the number of
	 * defenders, M is the number of midfielders and A is the number of attackers) - or "chaotic" if the
	 * formation is not discernable
	 * @param conn				the connection to the game database
	 * @param team				either 0 or 1 for the first or second team respectively
	 * @return					the formation in a string
	 * @throws SQLException
	 * @throws Exception
	 */
	public String getTeamFormation(Connection conn, int team) throws SQLException, Exception {		
		String q =  "select object,"+ 
			        " avg(x)"+ (team == 0 ? "" : "*-1") + " as mean_x," +
			        " avg(y) as mean_y" + 
					" from motion"+
					" where object between " + (team == 0 ? "0 and 10" : "11 and 21") +
					" group by object" + 
					" order by mean_x"; // limit 1,10"; // exclude the goalkeeper		
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(q);

		// the first player is the goalkeeper
		rs.next();
		goalkeepers.add(rs.getInt("object"));
		
		// add players with distance to next player to a vector (9 distances between 10 players, since the goalkeeper is exluded)
		Vector<PlayerPos> players = new Vector<PlayerPos>();
		rs.next();
		double x = rs.getDouble("mean_x"), y = rs.getDouble("mean_y");
		int player = rs.getInt("object");
		for(int i = 0; i < 9; i++) {
			rs.next();
			double next_x = rs.getDouble("mean_x");
			int next_player = rs.getInt("object");			
			players.add(new PlayerPos(player, next_player, x, next_x - x, y, i));
			x = next_x;
			y = rs.getDouble("mean_y");
			player = next_player;
		}	
		// store the position of the last player for later (no distance to next player)
		PlayerPos last_player = new PlayerPos(player, -1, x, -1, y, 10);
		
		String formation;		
		Vector<PlayerPos> sorted_players = (Vector<PlayerPos>) players.clone();
		Collections.sort(sorted_players);
		
		if(sorted_players.get(1).dist < 5700) { // for good teams, the diffs are even clearly greater than 8000  
			formation = "chaotic";
			System.out.println("  chaotic");
		}
		else {			
			int division_indices[] = new int[2]; 
			for(int i = 0; i <= 1; i++)
				division_indices[i] = sorted_players.get(i).index;
			Arrays.sort(division_indices);
			
			int numDefenders = division_indices[0]+1;
			int numMidfielders = division_indices[1]-division_indices[0];
			int numAttackers = 9-division_indices[1];
			
			// If only one midfielder is detected, it could be a defensive centre midfielder that is pulled back.
			if(numMidfielders == 1) {  
				// look for the biggest gap in the attackers
				double max_dist = 0;
				int max_dist_idx = -1;
				for(int i = division_indices[1]+1; i < 9; i++) {
					if(players.get(i).dist > max_dist) {
						max_dist = players.get(i).dist;
						max_dist_idx = i;
					}
				}
				// if the gap is big enough, make the ones further back midfielders
				if(max_dist > 4000) {
					division_indices[1] = max_dist_idx;
					numMidfielders = max_dist_idx - division_indices[0];
					numAttackers = 9 - max_dist_idx;
				}
			}
			
			formation = String.format("Form%d%d%d", numDefenders, numMidfielders, numAttackers);
			System.out.println("  " + formation + " " + sorted_players.get(2).dist + " (" + sorted_players.get(2).player%11 +"-" + sorted_players.get(2).next_player%11+ ")");
			
			// assign player roles (defenders, midfielders, attackers)
			players.add(last_player); // include the last player
			PlayerGroup[] roles = new PlayerGroup[]{midfielders, attackers};
			PlayerGroup role = this.defenders;			
			for(int i = 0, j = 0; i <= 9; i++) {
				role.add(players.get(i));
				if(j < division_indices.length && division_indices[j] == i) {
					role = roles[j++];
				}
			}			
		}
		return formation;
	}

	public String getPosition(int playerNo) {
		if(goalkeepers.contains(playerNo))
			return "goalkeeper";
		if(defenders.contains(playerNo))
			return "defender";
		if(attackers.contains(playerNo))
			return "attacker";
		if(midfielders.contains(playerNo))
			return "midfielder";
		return "libero";
	}
	
	public String getPositionEx(int playerNo) {
		if(goalkeepers.contains(playerNo))
			return "keeper";
		if(defenders.contains(playerNo))
			return defenders.getRole(playerNo);
		if(attackers.contains(playerNo))
			return attackers.getRole(playerNo);
		if(midfielders.contains(playerNo))
			return midfielders.getRole(playerNo);
		return "libero";		
	}
	
	public String getPlayerCondition() {
		if(teamNo == 0)
			return " between 0 and 10 ";
		else
			return " between 11 and 21 ";
	}

	public String getOtherTeamPlayerCondition() {
		if(teamNo == 1)
			return " between 0 and 10 ";
		else
			return " between 11 and 21 ";
	}
	
	/**
	 * returns the number of times a team's defense has been bypassed in a certain game
	 * @param connGame			the connection to the game database
	 * @param team				either 0 or 1 for the first or second team respectively
	 * @return					the number of times the team's defense was bypassed in the game	
	 * @throws SQLException
	 */
	public static int getDefenseBypassed(Connection connGame, int team) throws SQLException {
		String q;
		if(team == 0) {
			q = "select count(*) " + 
				"from episodes e join " +
				        "(select time, min(x) as mins from episodes_positions " +
				         "where object between 1 and 10 group by time) as p1 " +
				         "on tstart=p1.time join " +
				         "(select time, min(x) as mine from episodes_positions " +
				          "where object between 1 and 10 group by time) as p2 " +
				          "on tend=p2.time " +
				"where e.player between 11 and 21 and xs>=mins and xe<=mine";
		}
		else {
			q = "select count(*) " + 
				"from episodes e join " + 
				        "(select time, max(x) as maxs from episodes_positions " + 
				        " where object between 11 and 21 group by time) as p1 " + 
				        "on tstart=p1.time join " + 
				        "(select time, max(x) as maxe from episodes_positions " + 
				        " where object between 11 and 21 group by time) as p2 " + 
				        "on tend=p2.time " + 
				"where e.player between 0 and 10 and xs<=maxs and xe>=maxe";
		}			
		Statement stmt = connGame.createStatement();
		ResultSet rs = stmt.executeQuery(q);
		rs.next();
		return rs.getInt(1);
	}
}
