package edu.tum.cs.srldb.fipm;

import edu.tum.cs.srldb.*;
import java.sql.*;

public class Shot extends BallAction {
	/**
	 * creates a Shot object
	 * @param db the database this object is to be part of (upon commit)
	 * @param rs the result set that contains the data for this object
	 * @throws Exception
	 */
	public Shot(Database db, ResultSet rs) throws Exception {
		super(db, rs);
		this.addAttribute("shot", "true");
		this.addAttribute("speed", rs.getString("speed"));
		this.addAttribute("angle", rs.getString("angle"));
	}
}
