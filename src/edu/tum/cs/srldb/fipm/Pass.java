package edu.tum.cs.srldb.fipm;

import edu.tum.cs.srldb.*;
import java.sql.*;

public class Pass extends BallAction {
	/**
	 * creates a Pass object
	 * @param db the database this object is to be part of (upon commit)
	 * @param rs the result set that contains the data for this object
	 * @param from the player this pass originated from (may be null if no link "passFrom" should be added)
	 * @param to the player the ball was passed to (may be null if no link "passTo" should be added)
	 * @throws Exception
	 */
	public Pass(Database db, ResultSet rs, Player from, Player to) throws Exception {
		super(db, rs);
		this.addAttribute("pass", "true");
		this.addAttribute("safePass", rs.getString("safe"));
		this.addAttribute("speed", rs.getString("speed"));
		this.addAttribute("direction", rs.getString("direction"));
		this.addAttribute("length", rs.getString("length"));	
		/*if(from != null)
			link("passFrom", from);*/
		if(to != null)
			link("passTo", to);
	}
}
