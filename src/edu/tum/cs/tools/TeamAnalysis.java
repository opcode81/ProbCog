package edu.tum.cs.tools;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/**
 * contains functions for the analysis of teams in FIPM
 * @author Dominik Jain
 */
public class TeamAnalysis {
	/**
	 * returns the formation of the team as a string in the format "D-M-A", where D is the number of
	 * defenders, M is the number of midfielders and A is the number of attackers. 
	 * @param conn				the connection to the game database
	 * @param team				either 0 or 1 for the first or second team respectively
	 * @return					the formation in a string
	 * @throws SQLException
	 * @throws Exception
	 */
	public static String getTeamFormation(Connection conn, int team) throws SQLException, Exception {		
		String q =  "select object,"+ 
			        "avg(x)"+ (team == 0 ? "" : "*-1") + " as mean_x"+
					" from motion"+
					" where object between " + (team == 0 ? "0 and 10" : "11 and 21") +
					" group by object" + 
					" order by mean_x limit 1,10"; // exclude the goalkeeper		
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(q);
		double[] diffs = new double[9];
		rs.next();
		double prev = rs.getDouble("mean_x");
		for(int i = 0; i < diffs.length; i++) {
			rs.next();
			double cur = rs.getDouble("mean_x"); 
			diffs[i] = cur - prev;
			prev = cur;
		}
		double[] sorted_diffs = diffs.clone();
		Arrays.sort(sorted_diffs);		
		if(sorted_diffs[7] < 5700) // for good teams, the diffs are even clearly greater than 8000  
			return "chaotic";
		int division_indices[] = new int[2], k = 0; 
		for(int i = 8; i >= 7; i--)
			for(int j = 0; j < diffs.length; j++)
				if(sorted_diffs[i] == diffs[j])
					division_indices[k++] = j;
		Arrays.sort(division_indices);
		String formation = String.format("%d-%d-%d", division_indices[0]+1, division_indices[1]-division_indices[0], 9-division_indices[1]);
		//System.out.print(formation);
		//System.out.println("  diff: " + sorted_diffs[7]);
		return formation;
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
