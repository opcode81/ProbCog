package edu.tum.cs.srldb.fipm;

import java.util.HashMap;
import de.tum.in.fipm.base.data.GameData;

import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Object;

public class Game extends Object {

	protected GameData gameData;
	protected HashMap<Integer, Player> players;
	
	public Game(Database database, GameData data) {
		super(database);
		this.players = new HashMap<Integer,Player>();
		this.gameData = data;
		int scoreDiff = gameData.getHomeScore() - gameData.getAwayScore();
		addAttribute("draw", scoreDiff == 0 ? "true" : "false");
	}
	
	public GameData getData() {
		return gameData;
	}
	
	public void addPlayer(int playerNo, Player player) {
		players.put(new Integer(playerNo), player);
	}
	
	public Player getPlayer(int playerNo) {
		return players.get(new Integer(playerNo));
	}
}
