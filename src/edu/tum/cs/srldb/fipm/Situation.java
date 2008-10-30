package edu.tum.cs.srldb.fipm;

import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DDException;

import java.sql.*;
import fipm.data.db.models.DBMotionReader;
import de.tum.in.fipm.base.data.ReaderException;
import de.tum.in.fipm.base.models.motion.IMotion;
import de.tum.in.fipm.base.models.motion.MotionQueryException;
import de.tum.in.fipm.base.models.util.Position;
import de.tum.in.fipm.base.models.util.GameData;

public class Situation extends Object {
	
	static final int fieldXDim = 56000;
	static final int fieldYDim = 35220;
	/**
	 * the max distance between two opposing players so that they count as marked
	 */
	static final int distMarked = 6 * fieldXDim / 100; 
	
	protected PositionEx ballPos;
	
	protected class PositionEx extends Position {	
		public PositionEx(Connection conn, GameData gameData, int object, int time, DBMotionReader mr) throws SQLException, MotionQueryException, ReaderException {
			super(-1,-1);
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select * from motion where object=" + object + " and time <=" + time + " order by time desc limit 1");			
			IMotion motion = mr.getMotionsFromResult(rs).get(0);
			Position pos = motion.queryPosition(time);
			this.x = pos.x;
			this.y = pos.y;
		}
		
		public PositionEx(float x, float y) {
			super(x, y);
		}
		
		public double vectorLength() {
			return Math.sqrt(x*x+y*y);
		}
		
		/**
		 * determines the angle between points a and b relative to this
		 * @param a
		 * @param b
		 * @return an angle in degrees between -180 and 180
		 */
		public double angleBetween(PositionEx a, PositionEx b) {
			double x1 = a.x - this.x;
			double y1 = a.y - this.y;
			double x2 = b.x - this.x;
			double y2 = b.y - this.y;
			double dotprod = x1*x2 + y1*y2;
			double angle = java.lang.Math.acos(dotprod / a.vectorLength() / b.vectorLength());
			return angle * 180.0 / Math.PI;			 
		}
	}

	protected class Sector {
		public int teamMates;
		public int unmarkedTeamMates;
		public int opponents;
		
		public Sector() {
			teamMates = opponents = 0;
		}
		
		public void inc(boolean opponent) {
			if(opponent)
				opponents++;
			else
				teamMates++;
		}
		
		public String getPresence() {
			int diff = teamMates - opponents;
			if(diff > 0)
				return "majority";
			if(diff == 0)
				return "even";			
			return "minority";
		}
	}
	
	/**
	 * constructs a situation, determining the player in possession automatically (as the one closest to the ball)
	 * @param connGame
	 * @param game
	 * @param time
	 * @param mr
	 * @throws MotionQueryException
	 * @throws SQLException
	 * @throws DDException 
	 * @throws ReaderException 
	 */
	public Situation(Connection connGame, Game game, int time, DBMotionReader mr) throws MotionQueryException, SQLException, DDException, ReaderException {
		this(connGame, game, time, mr, null);
	}
	
	public Situation(Connection connGame, Game game, int time, DBMotionReader mr, Player playerInPossession) throws MotionQueryException, SQLException, DDException, ReaderException {
		super(game.getDatabase());
		
		GameData gameData = game.getData();
		
		// get ball position
		ballPos = new PositionEx(connGame, gameData, -1, time, mr);		
		// player position data
		int[] players = gameData.getPlayers(time);
		PositionEx[] playerPos = new PositionEx[players.length];
		int[] playerTeam = new int[players.length];		
		// - get player positions and, if the player in possession is not given, find the one closest to the ball
		boolean determinePossession = (playerInPossession == null);
		int playerNoInPossession = -1;
		int teamInPossession = -1;
		PositionEx activePlayerPos = null;// 
		double closestDistToBall = Double.MAX_VALUE;		 
		for(int i = 0; i < players.length; i++) {
			playerPos[i] = new PositionEx(connGame, gameData, players[i], time, mr);
			playerTeam[i] = gameData.getTeam4Player(players[i]);			
			if(determinePossession) {
				double dist = ballPos.distance(playerPos[i]);
				if(dist < closestDistToBall) {
					closestDistToBall = dist;
					activePlayerPos = playerPos[i]; 
					playerNoInPossession = players[i];
					teamInPossession = playerTeam[i];
				}
			}
		}
		if(!determinePossession) {
			teamInPossession = playerInPossession.team.teamNo;
			playerNoInPossession = playerInPossession.playerNo;
			activePlayerPos = new PositionEx(connGame, gameData, playerInPossession.playerNo, time, mr);
		}
		// - count players in sectors
		Sector forwardSector = new Sector();
		Sector backSector = new Sector();
		Sector crossSector = new Sector();
		PositionEx targetGoalCentre = new PositionEx(teamInPossession == 0 ? fieldXDim : -fieldXDim, 0);
		PositionEx forwardDirection = new PositionEx(targetGoalCentre.x, ballPos.y);
		for(int i = 0; i < players.length; i++) {			
			boolean isOpponent = playerTeam[i] != teamInPossession;
			// determine sector
			double angle = ballPos.angleBetween(forwardDirection, playerPos[i]);
			Sector s;
			if(angle < 66.4218215217984) // <=> FIPM's attack_direction > 0.7 
				s = forwardSector;
			else if(angle < 113.578178478202) // <=> FIPM's attack_direction between 0.3 and 0.7 
				s = crossSector;
			else // attack_direction < 0.3
				s = backSector;
			s.inc(isOpponent);
			// if player is a teammate, check if he's marked
			if(!isOpponent && players[i] != playerNoInPossession) {
				float minDist = fieldXDim;
				for(int j = 0; j < players.length; j++) {
					if(playerTeam[j] != teamInPossession) {
						float dist = playerPos[i].distance(playerPos[j]);
						if(dist < minDist)
							minDist = dist;
					}
				}
				if(minDist > distMarked)
					s.unmarkedTeamMates++;
				/*else
					System.out.println("marked");*/
			}			
		}		
		addAttribute("forwardPresence", forwardSector.getPresence());
		addAttribute("crossPresence", crossSector.getPresence());		
		addAttribute("backPresence", backSector.getPresence());
		addAttribute("unmarkedForwardMates", forwardSector.unmarkedTeamMates > 0 ? "true" : "false");
		addAttribute("unmarkedCrossMates", crossSector.unmarkedTeamMates > 0 ? "true" : "false");
		addAttribute("unmarkedBackMates", backSector.unmarkedTeamMates > 0 ? "true" : "false");	
		
		// ball position attributes		
		setBallPositionAttrs(teamInPossession);
		
		// set possession (as link)
		link("possession", game.getPlayer(playerNoInPossession));
	}	
	
	public void setPossession(Player player) throws DDException {
		Link possession = this.getLink("possession");
		possession.setSecondArgument(player);
		setBallPositionAttrs(player.team.teamNo);
	}
	
	protected void setBallPositionAttrs(int teamNo) throws DDException {
		float targetX = teamNo == 0 ? fieldXDim : -fieldXDim;
		float base = -targetX;
		float percent =  (ballPos.x-base) / (targetX-base);
		String posX;
		if(percent <= 0.25)
			posX = "defense";
		else if(percent <= 0.5)
			posX = "defMidfield";
		else if(percent <= 0.75)
			posX = "offMidfield";
		else
			posX = "attack";
		addAttribute("ballPosX", posX);
		addAttribute("ballPosY", Math.abs(ballPos.y) < fieldYDim/3 ? "centre" : "side");		
	}
}
