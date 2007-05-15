package edu.tum.cs.srldb.fipm;

import edu.tum.cs.srldb.*;
import java.sql.*;

public class Dribble extends BallAction {
	/**
	 * creates a Dribble object
	 * @param db the database this object is to be part of (upon commit)
	 * @param rs the result set that contains the data for this object
	 * @throws Exception
	 */
	public Dribble(Database db, ResultSet rs) throws Exception {
		super(db, rs);
		this.addAttribute("dribble", "true");
		this.addAttribute("speed", rs.getString("speed"));
		this.addAttribute("direction", rs.getString("direction"));
		this.addAttribute("length", rs.getString("length"));	
		this.addAttribute("outplayedOpponents", rs.getString("outplayedOpponents"));
	}
}
