package probcog.logic;

import probcog.srl.GenericDatabase;
import probcog.srl.Signature;
import probcog.srl.directed.RelationalBeliefNetwork;

public class PossibleWorldFromDatabase implements IPossibleWorld {

	GenericDatabase<?,?> db;
	RelationalBeliefNetwork rbn;
	boolean closedWorld;
	
	public PossibleWorldFromDatabase(RelationalBeliefNetwork rbn, GenericDatabase<?,?> db, boolean closedWorld) {
		this.db = db;
		this.rbn = rbn;
		this.closedWorld = closedWorld;
	}

	public boolean isTrue(GroundAtom ga) {
		try {
			Signature sig = rbn.getSignature(ga.predicate);			
			if(sig.isBoolean()) {
				String value = db.getSingleVariableValue(ga.toString(), closedWorld);
				if(value == null)
					throw new RuntimeException("Value of " + ga + " not in the database that is used as a possible world; perhaps it must always be given because it is used in a precondition/decision node.");
				boolean tv = value.equalsIgnoreCase("True");
				//System.out.println("value of atom " + ga + " corresponding to boolean function is " + (tv ? "true" : "false"));
				return tv;
			}
			else {
				String varName = rbn.gndAtom2VarName(ga);
				String value = db.getSingleVariableValue(varName, closedWorld);
				if(value == null)
					throw new RuntimeException("Value of " + varName + " not in the database that is used as a possible world; perhaps it must always be given because it is used in a precondition/decision node.");
				boolean tv = value.equals(ga.args[ga.args.length-1]);
				//System.out.println("value of atom " + ga + " corresponding to non-boolean function is " + (tv ? "true" : "false"));
				return tv;
			}
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}
