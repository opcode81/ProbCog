package edu.tum.cs.srldb.fipm;

import java.sql.Connection;
import java.sql.SQLException;

import de.tum.in.fipm.base.data.ReaderException;
import de.tum.in.fipm.base.models.motion.MotionQueryException;
import edu.tum.cs.srldb.datadict.DDException;
import fipm.data.db.models.DBMotionReader;

public class NumberedSituation extends Situation {

	protected int number;
	
	public NumberedSituation(Connection connGame, Game game,
			int time, DBMotionReader mr, int number, Player playerInPossession)
			throws MotionQueryException, SQLException, DDException, ReaderException {
		super(connGame, game, time, mr, playerInPossession);
		this.number = number;
	}

	public NumberedSituation(Connection connGame, Game game,
			int time, DBMotionReader mr, int number)
			throws MotionQueryException, SQLException, DDException, ReaderException {
		this(connGame, game, time, mr, number, null);
	}

	public String getConstantName() {
		return Integer.toString(number);
	}	
	
	public String objType() {
		return "Situation";
	}
}
