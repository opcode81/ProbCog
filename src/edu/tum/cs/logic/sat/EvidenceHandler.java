/*
 * Created on Aug 17, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.logic.sat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.WorldVariables.Block;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Database.Variable;
import edu.tum.cs.util.datastruct.Map2Set;

// TODO we could speed some of this up by explicitly keeping track of blocked and unblocked non-evidence vars 

public class EvidenceHandler {
	
	protected static boolean verbose = false;
	protected HashMap<Integer,Boolean> evidence;
	protected HashSet<Block> evidenceBlocks; 
	protected Map2Set<Block, GroundAtom> blockExclusions;
	protected WorldVariables vars;
	protected Random rand;
	
	public EvidenceHandler(WorldVariables vars, Database db) throws Exception {
		this.vars = vars;
		this.rand = new Random();

		if(verbose) {
			System.out.println("evidence:");
			db.print();
		}
		this.evidence = new HashMap<Integer,Boolean>();
		evidenceBlocks = new HashSet<Block>();
		blockExclusions = new Map2Set<Block,GroundAtom>();
		for(Variable var : db.getEntries()) {
			String strGndAtom = var.getPredicate(db.model);
			GroundAtom gndAtom = vars.get(strGndAtom);
			if(gndAtom == null)
				throw new Exception("Evidence ground atom '" + strGndAtom + "' not in set of world variables.");
			if(!var.isBoolean(db.model)) { // if the variable isn't boolean, it is mapped to a block, which we set in its entirety
				Block block = vars.getBlock(gndAtom.index);
				if(block == null) 
					throw new Exception(String.format("There is no variable block to which the non-boolean variable assignment '%s' can be mapped.", var.toString()));				
				for(GroundAtom ga : block)
					this.evidence.put(ga.index, var.value.equals(ga.args[ga.args.length-1]));				
				evidenceBlocks.add(block);
			}
			else {
				boolean truthValue = var.isTrue();
				this.evidence.put(gndAtom.index, truthValue);
				Block block = vars.getBlock(gndAtom.index);
				if(block != null) {
					if(truthValue == true) {
						for(GroundAtom ga : block)
							this.evidence.put(ga.index, ga.index == gndAtom.index);
						evidenceBlocks.add(block);
					}
					else {
						blockExclusions.add(block, gndAtom);
					}
				}
			}
		}
	}
	
	public void setEvidenceInState(PossibleWorld state) {
		for(Entry<Integer, Boolean> e : this.evidence.entrySet()) 
			state.set(e.getKey(), e.getValue());
	}
	
	/**
	 * sets a random state for the non-evidence atoms
	 * @param state
	 */
	public void setRandomState(PossibleWorld state) {
		HashSet<Block> handledBlocks = new HashSet<Block>();
		for(int i = 0; i < vars.size(); i++) {
			//System.out.println("  setting " + vars.get(i));
			Block block = vars.getBlock(i); 
			if(block != null) {
				if(this.evidenceBlocks.contains(block) || handledBlocks.contains(block))
					continue;
				// if we do not need to handle block exclusions, we just set any ground atom in the block to true and the others to false
				Set<GroundAtom> excl = blockExclusions.get(block);
				if(excl == null) {
					int j = rand.nextInt(block.size());
					for(int k = 0; k < block.size(); k++) {
						boolean value = k == j; 
						state.set(block.get(k), value);
					}
				}
				// if we do need to handle exclusions, collect a set of possible true ones and set one of them to true
				else {
					Vector<GroundAtom> possibleTrueOnes = new Vector<GroundAtom>();
					for(GroundAtom gndAtom : block) {
						if(!excl.contains(gndAtom))
							possibleTrueOnes.add(gndAtom);
					}
					GroundAtom trueOne = possibleTrueOnes.get(rand.nextInt(possibleTrueOnes.size()));
					for(GroundAtom gndAtom : block) 
						state.set(gndAtom, trueOne == gndAtom);					
				}
				handledBlocks.add(block);
			}
			else { 
				if(!this.evidence.containsKey(i))
					state.set(i, rand.nextBoolean());
			}
		}	
	}
	
	public HashMap<Integer, Boolean> getEvidence() {
		return evidence;
	}	
}
