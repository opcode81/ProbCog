package edu.tum.cs.srldb.fipm;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.util.*;
import edu.tum.cs.util.db.DatabaseTool;

import java.sql.*;


public class Player extends Object {
	
	public Team team;
	public int playerNo;
	
	public Player(Connection conn, Team team, int playerNo) throws SQLException, DDException {
		super(team.getDatabase());

		this.team = team;
		this.playerNo = playerNo;
		
		this.addAttribute("position", team.getPosition(playerNo));
		
		// exact position
		String positionEx = team.getPositionEx(playerNo);
		System.out.println(String.format("    %d %s", playerNo, positionEx));
		addAttribute("positionEx", positionEx);
		
		// get the number of ball actions
		String ballActions = DatabaseTool.queryResult(conn, "select count(*) from episodes where player=" + playerNo);
		addAttribute("ballActions", ballActions);
		if(Integer.parseInt(ballActions) == 0)
			ballActions = "1";
		
		// number of ball acqusitions
		String query = "select count(*) from episodes as e where player=" + playerNo + " and exists (select * from episodes where tend=e.tstart and player " + team.getOtherTeamPlayerCondition() +")";
		addAttribute("ballAcquisitions", DatabaseTool.queryResult(conn, query));
		
		// number of ball losses
		addAttribute("ballLosses", DatabaseTool.queryResult(conn, "select count(*) from episodes as e where player=" + playerNo + " and exists (select * from episodes where tstart=e.tend and player " + team.getOtherTeamPlayerCondition()+")"));
		
		// maximum speed
		addAttribute("runningSpeed", DatabaseTool.queryResult(conn, "select max(sqrt(pow(x-x_end,2)+pow(y-y_end,2)) / (t_end-time)) as speed from motion where object="+playerNo));

		// ball actions: share of... 
		// - passes
		String passes = addShareAttribute(conn, "passShare", ballActions, "select count(*) from episodes_pass natural join episodes where player=" + playerNo);
		// - dribblings		
		addShareAttribute(conn, "dribblingShare", ballActions, "select count(*) from episodes_dribble natural join episodes where player=" + playerNo);
		// - shots
		addShareAttribute(conn, "shotShare", ballActions, "select count(*) from episodes_shot natural join episodes where player=" + playerNo);
		
		// passes: share of...
		if(Integer.parseInt(passes) == 0)
			passes = "1";
		// - back passes
		addShareAttribute(conn, "backPassShare", passes, "select count(*) from episodes_pass natural join episodes where player="+playerNo+" and detail_direction=0");
		// - cross passes
		addShareAttribute(conn, "crossPassShare", passes, "select count(*) from episodes_pass natural join episodes where player="+playerNo+" and detail_direction=1");
		// - attack passes  
		addShareAttribute(conn, "attackPassShare", passes, "select count(*) from episodes_pass natural join episodes where player="+playerNo+" and detail_direction=2");
		// - successful passes  
		addShareAttribute(conn, "succPassShare", passes, "select count(*) from episodes_pass natural join episodes where player="+playerNo+" and successful=1");
		
		// find out whether the player scored a goal
		int goalsScored = Integer.parseInt(DatabaseTool.queryResult(conn, "select count(*) from episodes natural join episodes_shot where successful=1 and player="+ playerNo));
		addAttribute("goalScored", goalsScored > 0 ? "true" : "false");
		
		// connect to team
		link("inTeam", team);
	}
	
	public String addShareAttribute(Connection conn, String attrName, String reference, String query) throws SQLException, NumberFormatException, DDException {
		String res = DatabaseTool.queryResult(conn, query);
		addAttribute(attrName, Double.toString(Double.parseDouble(res) / Double.parseDouble(reference)));
		return res;
	}
	
	public String toString() {
		return Integer.toString(this.playerNo);
	}
}
