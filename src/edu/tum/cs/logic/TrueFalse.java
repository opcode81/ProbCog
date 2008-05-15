package edu.tum.cs.logic;

import java.util.HashMap;
import java.util.Set;

import edu.tum.cs.bayesnets.relational.core.Database;

public class TrueFalse extends Formula {
	
	public static TrueFalse FALSE = new TrueFalse(false);
	public static TrueFalse TRUE = new TrueFalse(true); 

	public static TrueFalse getInstance(boolean isTrue) {
		return isTrue ? TRUE : FALSE;
	}
	
	protected boolean isTrue;
	
	private TrueFalse(boolean isTrue) {
		this.isTrue = isTrue;
	}	
	
	@Override
	public void getVariables(Database db, HashMap<String, String> ret) {
	}

	@Override
	public Formula ground(HashMap<String, String> binding, WorldVariables vars, Database db) throws Exception {
		return this;
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
	}

	@Override
	public boolean isTrue(PossibleWorld w) {
		return isTrue;
	}
	
	@Override
	public String toString() {
		return isTrue ? "True" : "False";
	}
}
