package edu.tum.cs.logic;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.relational.core.Signature;

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
			Signature sig = rbn.getSignature(ga.predicate);			
			if(sig.isBoolean()) {
				String value = db.getVariableValue(ga.toString(), closedWorld);
				if(value == null)
					throw new RuntimeException("Value of " + ga + " not in database; however it must always be given because it is used in a precondition/decision node.");
				return value.equalsIgnoreCase("True");
			}
			else {
				String varName = rbn.gndAtom2VarName(ga);
				String value = db.getVariableValue(varName, closedWorld);
				if(value == null)
					throw new RuntimeException("Value of " + varName + " not in database; however it must always be given because it is used in a precondition/decision node.");
				return value.equals(ga.args[ga.args.length-1]);			
			}
		}
		catch(Exception e) {
			throw new RuntimeException(e.toString());
		}
	}
}
