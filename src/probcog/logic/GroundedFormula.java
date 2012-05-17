/*
 * Created on Aug 5, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.logic;

import java.util.Map;

import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;


public abstract class GroundedFormula extends Formula {

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) {
		return this;
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) {
		throw new UnsupportedOperationException("This method is not intended to be called on grounded formulas.");
	}

	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws Exception {
		// grounded formulas contain no variables; nothing to do
	}
}
