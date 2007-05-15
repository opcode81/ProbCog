package edu.tum.cs.srldb.fipm;

import java.sql.Connection;
import java.sql.SQLException;

import de.tum.in.fipm.base.models.motion.MotionQueryException;
import fipm.data.db.models.DBMotionReader;

public class NumberedSituation extends Situation {

	protected int number;
	
	public NumberedSituation(Connection connGame, Game game,
			int time, DBMotionReader mr, int number, Player playerInPossession)
			throws MotionQueryException, SQLException {
		super(connGame, game, time, mr, playerInPossession);
		this.number = number;
	}

	public NumberedSituation(Connection connGame, Game game,
			int time, DBMotionReader mr, int number)
			throws MotionQueryException, SQLException {
		this(connGame, game, time, mr, number, null);
	}

	public String MLNid() {
		return Integer.toString(number);
	}	
	
	public String objType() {
		return "Situation";
	}
}
