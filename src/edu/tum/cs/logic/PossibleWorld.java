package edu.tum.cs.logic;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;

public class PossibleWorld implements IPossibleWorld {
	
	WorldVariables worldVars;
	boolean[] state;

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

    /**
     * this method sets the groundatoms of the given hashset in this possible world to true or false
     * @param atoms atoms to be set (true or false)
     * @param value true value (atom which conatins this value is set true, all other atoms are set false)
     */
    public void setWorldofWCSP(HashSet<GroundAtom> atoms, String value) {
        Iterator<GroundAtom> it = atoms.iterator();
        GroundAtom g;
        // sets all atoms of the hashset true or false
        while (it.hasNext()) {
            g = it.next();
            // if atom does't contains value and value isn't boolean -> set atom false
            if (!(g.args[g.args.length - 1].hashCode() == value.hashCode()) && !value.equals("True"))
                set(g.index, false);
            else {
                // else set atom true and break
                set(g.index, true);
                break;
            }
        }

        // set all left atoms false
        while (it.hasNext())
            set(it.next().index, false);
    }

	public void print() {
		print(System.out);
	}

    public void print(PrintStream out) {
		out.println("world size: " + worldVars.size());
        for(int i = 0; i < worldVars.size(); i++) {
			out.println(worldVars.get(i).index + "   " + worldVars.get(i) + " " + worldVars.get(i).isTrue(this));
		}
	}

    public void setState(boolean[] state){
        if (state.length == this.state.length)
            this.state = state;
        else 
            throw new IllegalArgumentException("Size of state array does not match number of variables!");        
    }
}