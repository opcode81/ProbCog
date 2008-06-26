package edu.tum.cs.logic;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;

public class PossibleWorldFromDatabase implements IPossibleWorld {

	Database db;
	RelationalBeliefNetwork rbn;
	boolean closedWorld;
	
	public PossibleWorldFromDatabase(RelationalBeliefNetwork rbn, Database db, boolean closedWorld) {
		this.db = db;
		this.rbn = rbn;
		this.closedWorld = closedWorld;
	}

	public boolean isTrue(GroundAtom ga) {
		try {
			if(rbn.getSignature(ga.predicate).isBoolean()) {
				return db.getVariableValue(ga.toString(), closedWorld).equalsIgnoreCase("True");
			}
			else {
				String varName = rbn.gndAtom2VarName(ga);
				return db.getVariableValue(varName, closedWorld).equals(ga.args[ga.args.length-1]);			
			}
		}
		catch(Exception e) {
			throw new RuntimeException(e.toString());
		}
	}
}
