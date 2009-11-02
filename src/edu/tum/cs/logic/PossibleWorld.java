package edu.tum.cs.logic;

import java.io.PrintStream;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.RelationalModel;
import edu.tum.cs.srl.AbstractVariable;

public class PossibleWorld implements IPossibleWorld {
	
	protected WorldVariables worldVars;
	protected boolean[] state;

	public PossibleWorld(WorldVariables worldVars) {
		this.worldVars = worldVars;
		this.state = new boolean[worldVars.size()];
	}
	
	public PossibleWorld(WorldVariables worldVars, boolean[] state) {
		if(state.length != worldVars.size())
			throw new IllegalArgumentException("Size of state array does not much number of variables");
		this.worldVars = worldVars;
		this.state = state;
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

	public PossibleWorld clone() {
		return new PossibleWorld(worldVars, state.clone());
	}
	
	public boolean[] getState() {
		return state;
	}
	
	public WorldVariables getVariables() {
		return worldVars;
	}

	public void print() {
		print(System.out);
	}

    public void print(PrintStream out) {
		out.println("world size: " + worldVars.size());
        for(int i = 0; i < worldVars.size(); i++) {
        	GroundAtom ga = worldVars.get(i);
			out.println(ga.index + "   " + ga + " " + ga.isTrue(this));
		}
	}

    public void setState(boolean[] state){
        if (state.length == this.state.length)
            this.state = state;
        else 
            throw new IllegalArgumentException("Size of state array does not match number of variables!");        
    }
    
    public void setEvidence(Database db) {
    	for(AbstractVariable var : db.getEntries()) {
    		this.set(var.getPredicate(), var.isTrue()); 
    	}
    }
}