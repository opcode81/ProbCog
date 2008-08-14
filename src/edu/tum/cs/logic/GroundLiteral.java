package edu.tum.cs.logic;

import java.util.Map;
import java.util.Set;

import edu.tum.cs.bayesnets.relational.core.Database;

public class GroundLiteral extends Formula {
	protected boolean isPositive;
	protected GroundAtom gndAtom;
	
	public GroundLiteral(boolean isPositive, GroundAtom gndAtom) {
		this.gndAtom = gndAtom;
		this.isPositive = isPositive;
	}
	
	public boolean isTrue(IPossibleWorld w) {
		boolean v = w.isTrue(gndAtom);
		return isPositive ? v : !v;
	}

	@Override
	public void getVariables(Database db, Map<String, String> ret) {
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, Database db) throws Exception {
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

	@Override
	public Formula toCNF() {		
		return this;
	}
}
