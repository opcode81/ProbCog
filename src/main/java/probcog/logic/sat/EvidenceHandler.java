/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.logic.sat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import probcog.logic.GroundAtom;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.logic.WorldVariables.Block;
import probcog.srl.AbstractVariable;

import edu.tum.cs.util.datastruct.Map2Set;

// TODO we could speed some of this up by explicitly keeping track of blocked and unblocked non-evidence vars 

/**
 * Helper class for handling evidence.
 * @author Dominik Jian
 */
public class EvidenceHandler {
	
	protected static boolean verbose = false;
	protected HashMap<Integer,Boolean> evidence;
	protected HashSet<Block> evidenceBlocks; 
	protected Map2Set<Block, GroundAtom> blockExclusions;
	protected WorldVariables vars;
	
	public EvidenceHandler(WorldVariables vars, Iterable<? extends AbstractVariable<?>> db) throws Exception {
		this.vars = vars;

		this.evidence = new HashMap<Integer,Boolean>();
		evidenceBlocks = new HashSet<Block>();
		blockExclusions = new Map2Set<Block,GroundAtom>();
		for(AbstractVariable<?> var : db) {
			String strGndAtom = var.getPredicate();
			GroundAtom gndAtom = vars.get(strGndAtom);
			if(gndAtom == null) {
				if(var.pertainsToEvidenceFunction()) // pure evidence functions may be missing from the model altogether
					continue;
				else
					throw new Exception("Evidence ground atom '" + strGndAtom + "' not in set of world variables.");
			}
			if(!var.isBoolean()) { // if the variable isn't boolean, it is mapped to a block, which we set in its entirety
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
	 * sets a random state for the non-evidence atoms in the given state
	 * @param state the state in which to set the variable values
	 */
	public void setRandomState(PossibleWorld state) throws Exception {
		setRandomState(state, new Random());
	}
	
	/**
	 * sets a random state for the non-evidence atoms in the given state
	 * @param state the state in which to set the variable values
	 * @param rand the random number generator to use
	 */
	public void setRandomState(PossibleWorld state, Random rand) throws Exception {
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
					if(possibleTrueOnes.isEmpty())
						throw new Exception("Invalid Evidence: The block of variables " + block + " contains only false atoms");
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
