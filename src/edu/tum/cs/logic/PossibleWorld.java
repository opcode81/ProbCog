package edu.tum.cs.logic;

public class PossibleWorld implements IPossibleWorld {
	
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
	
	public void set(GroundAtom gndAtom, boolean value) {
		state[gndAtom.index] = value;
	}
	
	public void set(int idxGndAtom, boolean value) {
		state[idxGndAtom] = value;
	}
	
	public boolean get(int idxGndAtom) {
		return state[idxGndAtom];
	}
	
	public void print() {
		for(int i = 0; i < worldVars.size(); i++) {
			if(!state[i])
				System.out.print("!");
			System.out.println(worldVars.get(i));
		}
	}
}