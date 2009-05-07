package edu.tum.cs.srldb.fipm;
import java.sql.Connection;

import java.util.Vector;


import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DDRelationAttribute;
import edu.tum.cs.srldb.datadict.DataDictionary;
import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;
import edu.tum.cs.srldb.datadict.domain.OrderedStringDomain;
import fipm.data.db.models.DBMotionReader;


public class FIPMData {
	
	public Vector<Object> players, teams;
	public Vector<Vector<Object>> currentTeamPlayers; 
	public Database database;	
	public OrderedStringDomain domainLowHigh;
	public OrderedStringDomain domainCount;
	public DDObject ddTeam, ddGame, ddPlayer;
	public DataDictionary datadict;
	protected DBMotionReader motionReader;

	public FIPMData() throws Exception {
		// create the data dictionary
		datadict = new DataDictionary();
		domainLowHigh = new OrderedStringDomain("lowhigh", new String[]{"low", "medium", "high"});
		domainCount = new OrderedStringDomain("count", new String[]{"few", "average", "many"});
		// - game
		ddGame = new DDObject("Game");
		ddGame.addAttribute(new DDAttribute("draw", BooleanDomain.getInstance()));
		datadict.addObject(ddGame);
		// - team 
		ddTeam = new DDObject("Team");
		ddTeam.addAttribute(new DDAttribute("attacks", domainCount, true));			
		ddTeam.addAttribute(new DDAttribute("avgSidelineDist", domainLowHigh, true));
		ddTeam.addAttribute(new DDAttribute("avgShotVelocity", domainLowHigh, true));
		ddTeam.addAttribute(new DDAttribute("avgRunDistance", domainLowHigh, true));
		ddTeam.addAttribute(new DDAttribute("ballPossession", domainLowHigh, true));
		ddTeam.addAttribute(new DDAttribute("defenseEffectiveness", domainLowHigh, true));
		ddTeam.addAttribute(new DDAttribute("passAccuracy", domainLowHigh, true));
		ddTeam.addAttribute(new DDAttribute("formation", new AutomaticDomain("form")));
		ddTeam.addAttribute(new DDAttribute("ballInOpponentHalf", domainLowHigh, true));
		ddTeam.addAttribute(new DDAttribute("attackEffectiveness", domainLowHigh, true));
		datadict.addObject(ddTeam);
		// - player
		ddPlayer = new DDObject("Player");
		ddPlayer.addAttribute(new DDAttribute("attackPassShare", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("crossPassShare", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("backPassShare", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("succPassShare", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("shotShare", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("passShare", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("dribblingShare", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("runningSpeed", domainLowHigh, true));
		ddPlayer.addAttribute(new DDAttribute("ballActions", domainCount, true));
		ddPlayer.addAttribute(new DDAttribute("ballLosses", domainCount, true));
		ddPlayer.addAttribute(new DDAttribute("ballAcquisitions", domainCount, true));
		ddPlayer.addAttribute(new DDAttribute("position", new AutomaticDomain("pos")));
		ddPlayer.addAttribute(new DDAttribute("positionEx", new AutomaticDomain("posex")));
		ddPlayer.addAttribute(new DDAttribute("goalScored", BooleanDomain.getInstance())); 
		datadict.addObject(ddPlayer);		
		// - relations
		datadict.addRelation(new DDRelation("inTeam", ddPlayer, ddTeam, false, true));
		DDRelation ddPlayedIn = new DDRelation("playedIn", ddTeam, ddGame, false, false);
		ddPlayedIn.addAttribute(new DDRelationAttribute("win", new boolean[]{true, false}));
		ddPlayedIn.addAttribute(new DDRelationAttribute("lose", new boolean[]{true, false}));
		datadict.addRelation(ddPlayedIn);
	}
	
	public void fetch(String gameDBCond) throws Exception {
//		System.out.println("fetching data...");			
//
//		// load MySQL driver (TODO move to appropriate location)
//		Class.forName("com.mysql.jdbc.Driver").newInstance();
//
//		// initialize main query engine
//		Queryengine dbengine = Queryengine.getInstance();
//		if (!dbengine.check()) System.exit(1);
//		Settings.getStd().put("main_dbengine",dbengine);
//		
//		// get a list of all the games
//        DBGameDataIO gameIO = DBGameDataIO.getInstance();
//        Iterator<GameData> iGameData = null; //(Iterator<GameData>) gameIO.readAll(gameDBCond).iterator();
//        
//		// process all the listed game databases...	        		
//        database = new Database(datadict);
//        players = new Vector<Object>();
//        teams = new Vector<Object>();
//		for(int nGame = 0; iGameData.hasNext(); nGame++) {
//			GameData gameData = iGameData.next();
//			motionReader = DBMotionReader.getInstance(gameData);
//			
//			// connect to game database
//			String gameDB = gameData.getDatabase(); 
//			System.out.println(gameDB + " (" + gameData.getName() + ")");
//			Settings settings = Settings.getStd();
//			String connectString = "jdbc:mysql://" + settings.get("primary_host") + ":" + settings.get("primary_port") + "/" + gameDB + "?user=" + settings.get("primary_user") + "&password=" + settings.get("primary_password");
//			Connection connGame = DriverManager.getConnection(connectString);	
//			
//			// get game data
//			Game game = new Game(database, gameData);		
//			game.commit();
//			
//			// get team data
//			currentTeamPlayers = new Vector<Vector<Object>>();
//			for(int i = 0; i <= 1; i++) {				
//				Team team = new Team(connGame, game, i);
//				team.commit();
//				teams.add(team);
//				
//				// get the player data
//				Vector<Object> teamPlayers = new Vector<Object>();
//				for(int j = 0; j < 11; j++) {
//					int p = i*11+j;
//					Player player = new Player(connGame, team, p);
//					player.commit();
//					players.add(player);
//					teamPlayers.add(player);
//					game.addPlayer(p, player);					
//				}
//				currentTeamPlayers.add(teamPlayers);
//			}
//			
//			gameProc(connGame, nGame, game);
//		}
	}
	
	/**
	 * meant to be overridden
	 * @param connGame
	 * @param nGame
	 * @param game
	 * @throws Exception
	 */
	protected void gameProc(Connection connGame, int nGame, Game game) throws Exception {	
	}
	
	public void doClustering() throws Exception {
		System.out.println("clustering...");
		database.doClustering();
	}
}
