package edu.tum.cs.logic;

public class PossibleWorld {
	
	WorldVariables worldVars;
	boolean[] state;
	
	public PossibleWorld(WorldVariables worldVars) {
		this.worldVars = worldVars;
		state = new boolean[worldVars.size()];
	}
	
	public boolean isTrue(GroundAtom ga) {
		return state[ga.index];
	}
	
	public void set(String gndAtom, boolean value) {
		state[worldVars.get(gndAtom).index] = value;
	}
}
