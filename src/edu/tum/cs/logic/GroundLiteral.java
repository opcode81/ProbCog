package edu.tum.cs.logic;

import java.util.HashMap;
import java.util.Set;

import edu.tum.cs.bayesnets.relational.core.Database;

public class GroundLiteral extends Formula {
	protected boolean isPositive;
	protected GroundAtom gndAtom;
	
	public GroundLiteral(boolean isPositive, GroundAtom gndAtom) {
		this.gndAtom = gndAtom;
		this.isPositive = isPositive;
	}
	
	public boolean isTrue(PossibleWorld w) {
		boolean v = w.isTrue(gndAtom);
		return isPositive ? v : !v;
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
		ret.add(gndAtom);		
	}
	
	@Override
	public String toString() {
		return isPositive ? gndAtom.toString() : "!" + gndAtom.toString();
	}
}
