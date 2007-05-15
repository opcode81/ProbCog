package edu.tum.cs.srldb.fipm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map.Entry;

import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Object;

public class BallAction extends Object {
	public BallAction(Database db, ResultSet rs) throws SQLException {
		super(db);
		this.addAttribute("successful", rs.getString("successful"));
		// default values (to be overridden)
		this.addAttribute("pass", "false");
		this.addAttribute("shot", "false");
		this.addAttribute("dribble", "false");		
	}
	
	public String objType() {
		return "BallAction";
	}
	
	public void addSituationAttributes(Situation sit, String prefix) {
		for(Entry<String,String> entry : sit.getAttributes().entrySet()) {
			addAttribute(prefix + Database.upperCaseString(entry.getKey()), entry.getValue());
		}
	}
}
